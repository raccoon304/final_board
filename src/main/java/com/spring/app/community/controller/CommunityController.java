package com.spring.app.community.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.spring.app.community.domain.CommentDTO;
import com.spring.app.community.domain.CommunityPostDTO;
import com.spring.app.community.service.CommentService;
import com.spring.app.community.service.CommunityService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/community") /*5*/
public class CommunityController {

    private final CommunityService communityService;
    private final CommentService commentService;

    @Value("${app.login-url}")
    private String loginUrl;
    
    public CommunityController(CommunityService communityService, CommentService commentService) {
        this.communityService = communityService;
        this.commentService = commentService; 
    }

    @GetMapping
    public String community(Model model,
            @RequestParam(value="boardId", required=false) Long boardId,
            @RequestParam(value="page", defaultValue="1") int page,
            @RequestParam(value="sort", required=false) String sort,
            @RequestParam(value="search", required=false) String search,
            @RequestParam(value="type", required=false) String type) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String memberId = null;
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            memberId = auth.getName();
        }

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
            model.addAttribute("myPostCount", communityService.getMyPostCount(memberId));
            model.addAttribute("myCommentCount", communityService.getMyCommentCount(memberId));
        } else {
            model.addAttribute("myPostCount", 0);
            model.addAttribute("myCommentCount", 0);
        }

        return "community/community";
    }

    @GetMapping("/write")
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

    @PostMapping("/write")
    public String write(@RequestParam("boardId") Long boardId,
                        @RequestParam("title") String title,
                        @RequestParam("content") String content) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String memberId = auth.getName();

        communityService.writePost(boardId, title, content, memberId);
        return "redirect:/community?boardId=" + boardId;
    }

    @GetMapping("/view")
    public String view(@RequestParam("postId") Long postId,
                       Model model,
                       HttpSession session,
                       jakarta.servlet.http.HttpServletRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String memberId = null;
        if (auth != null && auth.isAuthenticated()
            && !auth.getName().equals("anonymousUser")) {
            memberId = auth.getName();
        }
        model.addAttribute("memberId", memberId);
        
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

    @PostMapping("/view")
    public String increaseView(@RequestParam("postId") Long postId) {
        communityService.increaseViewCount(postId);
        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/comment")
    public String writeComment(@RequestParam("postId") Long postId,
                               @RequestParam("content") String content,
                               @RequestParam(value="parentCommentId", required=false) Long parentCommentId,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return "redirect:/user-service/member/login";
        }

        commentService.insertComment(postId, content, parentCommentId, auth.getName());
        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/edit")
    public String editPost(@RequestParam("postId") Long postId,
                           @RequestParam("title") String title,
                           @RequestParam("content") String content) {
        communityService.editPost(postId, title, content);
        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/delete")
    public String deletePost(@RequestParam("postId") Long postId) {
        Long boardId = communityService.getPostById(postId).getBoardId();
        communityService.deletePost(postId);
        return "redirect:/community?boardId=" + boardId;
    }

    @PostMapping("/comment/edit")
    public String editComment(@RequestParam("commentId") Long commentId,
                              @RequestParam("postId") Long postId,
                              @RequestParam("content") String content) {
        commentService.editComment(commentId, content);
        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/comment/delete")
    public String deleteComment(@RequestParam("commentId") Long commentId,
                                @RequestParam("postId") Long postId) {
        commentService.deleteComment(commentId);
        return "redirect:/community/view?postId=" + postId;
    }

    @PostMapping("/report")
    public String report(@RequestParam("targetType") int targetType,
                         @RequestParam("targetId") Long targetId,
                         @RequestParam("reasonId") Long reasonId,
                         @RequestParam(value="reportContent", required=false) String reportContent,
                         @RequestParam("postId") Long postId,
                         RedirectAttributes redirectAttributes) {

        String memberId = SecurityContextHolder.getContext()
                            .getAuthentication().getName();
        try {
            communityService.insertReport(targetType, targetId, reasonId, reportContent, memberId);
            redirectAttributes.addFlashAttribute("reportMsg", "신고가 접수되었습니다.");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            redirectAttributes.addFlashAttribute("reportMsg", "이미 신고한 대상입니다.");
        }

        return "redirect:/community/view?postId=" + postId;
    }
}