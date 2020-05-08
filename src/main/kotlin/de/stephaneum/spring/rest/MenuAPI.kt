package de.stephaneum.spring.rest

import de.stephaneum.spring.Session
import de.stephaneum.spring.database.*
import de.stephaneum.spring.helper.ErrorCode
import de.stephaneum.spring.helper.LogService
import de.stephaneum.spring.helper.MenuService
import de.stephaneum.spring.helper.JsfService
import de.stephaneum.spring.helper.JsfEvent
import de.stephaneum.spring.rest.objects.Request
import de.stephaneum.spring.rest.objects.Response
import de.stephaneum.spring.scheduler.ConfigScheduler
import de.stephaneum.spring.scheduler.Element
import de.stephaneum.spring.security.CryptoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/menu")
class MenuAPI (
        private val jsfService: JsfService,
        private val logService: LogService,
        private val configScheduler: ConfigScheduler,
        private val menuService: MenuService,
        private val cryptoService: CryptoService,
        private val menuRepo: MenuRepo,
        private val userMenuRepo: UserMenuRepo,
        private val userRepo: UserRepo
) {

    @GetMapping("/info")
    fun getInfo(): Response.MenuInfo {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")
        val menuAdmin = menuService.isMenuAdmin(me)
        val menuID = configScheduler.get(Element.defaultMenu)
        val menu = menuRepo.findByIdOrNull(menuID?.toInt() ?: 0) ?: throw ErrorCode(500, "menu not found")
        return Response.MenuInfo(menuAdmin, menu)
    }

    @GetMapping("/writable")
    fun getMenu(): List<Menu> {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")
        return menuService.getWritable(me)
    }

    @GetMapping("/default-priority")
    fun getDefaultPriority(@RequestParam(required = false) id: Int?): Response.Priority {
        val children = menuRepo.findByParentId(id)
        return Response.Priority(children.minBy { it.priority }?.priority?.minus(1) ?: 10)
    }

    @PostMapping("/create", "/create/{parentID}")
    fun createMenu(@PathVariable(required = false) parentID: Int?, @RequestBody request: Menu) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")

        if(request.name.isBlank())
            throw ErrorCode(400, "empty name")

        // check
        val parent = when (parentID) {
            null -> {
                if(!menuService.isMenuAdmin(me))
                    throw ErrorCode(403, "no write access")

                null
            }
            else -> {
                val parent = menuRepo.findByIdOrNull(parentID) ?: throw ErrorCode(404, "parent not found")
                if(!menuService.canWrite(me, parent))
                    throw ErrorCode(403, "no write access")

                parent
            }
        }

        val menu = Menu(0, request.name, parent, request.priority, request.link, null, null, request.password, true)
        menuRepo.save(menu)
        logService.log(EventType.CREATE_MENU, me, request.name)
        jsfService.send(JsfEvent.SYNC_MENU)
    }

    @PostMapping("/update")
    fun updateMenu(@RequestBody request: Menu) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")

        // check
        val menu = menuRepo.findByIdOrNull(request.id) ?: throw ErrorCode(404, "menu not found")
        if(!menuService.canWrite(me, menu))
            throw ErrorCode(403, "no write access")

        menu.name = request.name
        menu.priority = request.priority
        menu.password = request.password
        menu.link = request.link
        menuRepo.save(menu)
        logService.log(EventType.EDIT_MENU, me, request.name)
        jsfService.send(JsfEvent.SYNC_MENU)
    }

    @ExperimentalUnsignedTypes
    @PostMapping("/delete/{menuID}")
    fun deleteMenu(@PathVariable menuID: Int, @RequestBody request: Request.Password) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")

        // check
        val menu = menuRepo.findByIdOrNull(menuID) ?: throw ErrorCode(404, "menu not found")
        if(!menuService.canWrite(me, menu))
            throw ErrorCode(405, "no write access")

        // password check only applied to "groups"
        if(menu.link == null && !cryptoService.checkPassword(request.password ?: "", me.password))
            throw ErrorCode(403, "wrong password")

        menuRepo.delete(menu)
        logService.log(EventType.DELETE_MENU, me, menu.name)
        jsfService.send(JsfEvent.SYNC_MENU)
    }

    @PostMapping("/default/{menuID}")
    fun setDefaultMenu(@PathVariable menuID: Int) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")

        // check
        if(!menuService.isMenuAdmin(me))
            throw ErrorCode(403, "you are not menu admin")

        if(!menuRepo.existsById(menuID))
            throw ErrorCode(404, "menu not found")

        configScheduler.save(Element.defaultMenu, menuID.toString())
        jsfService.send(JsfEvent.SYNC_VARIABLES)
    }

    @GetMapping("/rules")
    fun getWriteRules(): List<UserMenu> {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")
        if(me.code.role != ROLE_ADMIN)
            throw ErrorCode(403, "you are not admin")

        return userMenuRepo.findAll().toList()
    }

    @PostMapping("/rules/add")
    fun addWriteRule(@RequestBody request: Request.MenuWriteRule) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")
        if(me.code.role != ROLE_ADMIN)
            throw ErrorCode(403, "you are not admin")

        val user = userRepo.findByIdOrNull(request.user) ?: throw ErrorCode(404, "user not found")
        val menu = when {
            request.menu == null -> null
            else -> menuRepo.findByIdOrNull(request.menu) ?: throw ErrorCode(404, "menu not found")
        }
        val activeRules = userMenuRepo.findByUser(user)
        when {
            activeRules.isEmpty() -> userMenuRepo.save(UserMenu(0, user, menu))
            activeRules.any { it.menu == null } -> throw ErrorCode(409, "already menu admin")
            activeRules.any { it.menu == menu } -> throw ErrorCode(410, "rule already exists")
            else -> {
                if(menu == null)
                    userMenuRepo.deleteByUser(user) // reset, because user will be menu admin

                userMenuRepo.save(UserMenu(0, user, menu))
            }
        }
    }

    @PostMapping("/rules/delete")
    fun deleteWriteRule(@RequestBody request: Request.MenuWriteRule) {
        val me = Session.get().user ?: throw ErrorCode(401, "Login")
        if(me.code.role != ROLE_ADMIN)
            throw ErrorCode(403, "you are not admin")

        val user = userRepo.findByIdOrNull(request.user) ?: throw ErrorCode(404, "user not found")
        val menu = when {
            request.menu == null -> null
            else -> menuRepo.findByIdOrNull(request.menu) ?: throw ErrorCode(404, "menu not found")
        }

        userMenuRepo.deleteByUserAndMenu(user, menu)
    }
}