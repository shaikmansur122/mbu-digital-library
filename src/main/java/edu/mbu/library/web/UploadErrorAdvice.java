package edu.mbu.library.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;

/** Shows a friendly message, on the page the user came from, when an upload is larger than the limit. */
@ControllerAdvice
public class UploadErrorAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", "That file is too large. The limit is 25 MB.");
        String referer = request.getHeader("Referer");
        String host = request.getScheme() + "://" + request.getServerName();
        // only go back to our own pages
        if (referer != null && referer.startsWith(host)) {
            return "redirect:" + referer;
        }
        return "redirect:/home";
    }
}
