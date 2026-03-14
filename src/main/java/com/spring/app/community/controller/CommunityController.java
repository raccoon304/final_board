package com.spring.app.community.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.spring.app.community.domain.CommentDTO;
import com.spring.app.community.domain.CommunityPostDTO;
import com.spring.app.community.service.CommentService;
import com.spring.app.community.service.CommunityService;

import jakarta.servlet.http.HttpSession;

@Controller
public class CommunityController {

    private final CommunityService communityService;
    private final CommentService commentService;

    public CommunityController(CommunityService communityService, CommentService commentService) {
        this.communityService = communityService;
        this.commentService = commentService;
    }

    @GetMapping("/community")
    public String community(Model model,
            @RequestParam(value="boardId", required=false) Long boardId,
            @RequestParam(value="page", defaultValue="1") int page,
            @RequestParam(value="sort", required=false) String sort,
            @RequestParam(value="search", required=false) String search,
            @RequestParam(value="type", required=false) String type) {  // ← userDetails 파라미터 제거

        // ↓ SecurityContextHolder로 직접 가져오기
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String memberId = null;
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            memberId = auth.getName();
        }

        System.out.println("=== memberId: " + memberId);

        int totalCount = communityService.getTotalCount(boardId, search, type, memberId);
        int pageSize = 10;
        int totalPages = (totalCount == 0) ? 1 : (int) Math.ceil((double) totalCount / pageSize);
        model.addAttribute("totalPages", totalPages);

        model.addAttribute("boards", communityService.getBoards());
        model.addAttribute("posts", communityService.getPosts(boardId, page, sort, search, type, memberId));
        model.addAttribute("hotPosts", communityService.getHotPosts());
        model.addAttribute("recentHotPosts", communityService.getRecentHotPosts());

        model.addAttribute("pageSize", pageSize);
        model.addAttribute("currentBoardId", boardId);
        model.addAttribute("currentPage", page);
        model.addAttribute("sort", sort);
        model.addAttribute("type", type);

        if (memberId != null) {
            int postCount = communityService.getMyPostCount(memberId);
            int commentCount = communityService.getMyCommentCount(memberId);
            System.out.println("=== myPostCount: " + postCount);
            System.out.println("=== myCommentCount: " + commentCount);
            model.addAttribute("myPostCount", postCount);
            model.addAttribute("myCommentCount", commentCount);
        } else {
            System.out.println("=== 로그인 안됨");
            model.addAttribute("myPostCount", 0);
            model.addAttribute("myCommentCount", 0);
        }

        return "community/community";
    }
    
    
    //  writeForm 중복 제거 - 하나로 통합
    @GetMapping("/community/write")
    public String writeForm(Model model,
            @RequestParam(value="boardId", required=false) Long boardId) {

        model.addAttribute("boards", communityService.getBoards());

        if (boardId != null && boardId > 0) {
            model.addAttribute("selectedBoard", communityService.getBoardById(boardId));
        } else {
            model.addAttribute("selectedBoard", null);
        }

        model.addAttribute("post", new CommunityPostDTO());

        return "community/write";
    }

 // - 일반 form 방식
    @PostMapping("/community/write")
    public String write(@RequestParam("boardId") Long boardId,
                        @RequestParam("title") String title,
                        @RequestParam("content") String content) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String memberId = auth.getName();

        communityService.writePost(boardId, title, content, memberId);
        return "redirect:/community?boardId=" + boardId;
    }



    // 실제 페이지 렌더링은 GET
    @GetMapping("/community/view")
    public String view(@RequestParam("postId") Long postId,
                       Model model,
                       HttpSession session) {

        // 세션에 조회한 postId 저장해서 중복 방지
        String sessionKey = "viewed_post_" + postId;
        communityService.increaseViewCount(postId);

        CommunityPostDTO post = communityService.getPostById(postId);
        List<CommentDTO> comments = commentService.getCommentsWithReplies(postId);

        model.addAttribute("post", post);
        model.addAttribute("comments", comments);
        model.addAttribute("prevPost", communityService.getPrevPost(postId));
        model.addAttribute("nextPost", communityService.getNextPost(postId));
        model.addAttribute("reportReasons", communityService.getReportReasons());

        return "community/view";
    }
    
    @PostMapping("/community/view")
    public String increaseView(@RequestParam("postId") Long postId) {

        communityService.increaseViewCount(postId);

        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/community/comment")
    public String writeComment(@RequestParam("postId") Long postId,
                               @RequestParam("content") String content,
                               @RequestParam(value="parentCommentId", required=false) Long parentCommentId) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }
        
        commentService.insertComment(postId, content, parentCommentId, auth.getName());
        return "redirect:/community/view?postId=" + postId;
    }
    
 // 게시글 수정
    @PostMapping("/community/edit")
    public String editPost(@RequestParam("postId") Long postId,
                           @RequestParam("title") String title,
                           @RequestParam("content") String content) {
        communityService.editPost(postId, title, content);
        return "redirect:/community/view?postId=" + postId;
    }

    // 게시글 삭제
    @PostMapping("/community/delete")
    public String deletePost(@RequestParam("postId") Long postId) {
        Long boardId = communityService.getPostById(postId).getBoardId();
        communityService.deletePost(postId);
        return "redirect:/community?boardId=" + boardId;
    }

    
    // 댓글 수정
    @PostMapping("/community/comment/edit")
    public String editComment(@RequestParam("commentId") Long commentId,
                              @RequestParam("postId") Long postId,
                              @RequestParam("content") String content) {
        commentService.editComment(commentId, content);
        return "redirect:/community/view?postId=" + postId;
    }

    // 댓글 삭제
    @PostMapping("/community/comment/delete")
    public String deleteComment(@RequestParam("commentId") Long commentId,
                                @RequestParam("postId") Long postId) {
        commentService.deleteComment(commentId);
        return "redirect:/community/view?postId=" + postId;
    }

    // 신고 접수
    @PostMapping("/community/report")
    public String report(@RequestParam("targetType") int targetType,
                         @RequestParam("targetId") Long targetId,
                         @RequestParam("reasonId") Long reasonId,
                         @RequestParam(value="reportContent", required=false) String reportContent,
                         @RequestParam("postId") Long postId,
    					 RedirectAttributes redirectAttributes) {
        
        // UserDetails 대신 SecurityContextHolder 사용
        String memberId = SecurityContextHolder.getContext()
                            .getAuthentication().getName();
        try {
            communityService.insertReport(targetType, targetId, reasonId, reportContent, memberId);
            redirectAttributes.addFlashAttribute("reportMsg", "신고가 접수되었습니다.");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 중복 신고 시 에러 대신 메시지 처리
            redirectAttributes.addFlashAttribute("reportMsg", "이미 신고한 대상입니다.");
        }
        
        return "redirect:/community/view?postId=" + postId;
    }

    
    
}