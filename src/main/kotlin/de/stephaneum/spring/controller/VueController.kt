package de.stephaneum.spring.controller

import de.stephaneum.spring.Session
import de.stephaneum.spring.security.JwtService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import javax.servlet.http.HttpServletRequest

@Controller
class VueController (
        private val jwtService: JwtService
) {

    @GetMapping("/", "/menu/{id}", "/beitrag/{id}", "/statistiken", "/termine", "/geschichte", "/eu-sa", "/kontakt", "/impressum", "/sitemap",
            "/login", "/user-manager", "/config-manager", "/static-manager", "/code-manager", "/logs",
            "/plan-manager", "/menu-manager", "/post-manager", "/groups", "/groups/{id}", "/cloud", "/account")
    fun html(@RequestParam(required = false) key: String?, request: HttpServletRequest, @PathVariable(required = false) id: Int?): String {
        // login
        if(key != null) {
            Session.get().user = jwtService.getUser(key)
            if(Session.get().user != null) {
                return "redirect:${request.requestURL}"
            }
        }

        return "forward:/static/index.html"
    }
}