package de.stephaneum.spring.controller

import de.stephaneum.spring.Session
import de.stephaneum.spring.database.FileRepo
import de.stephaneum.spring.helper.FileService
import de.stephaneum.spring.scheduler.ConfigScheduler
import de.stephaneum.spring.scheduler.Element
import de.stephaneum.spring.security.JwtService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.*
import org.springframework.stereotype.Controller
import org.springframework.util.FileCopyUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import javax.servlet.http.HttpServletRequest

@Controller
class CloudController (
        private val fileService: FileService,
        private val jwtService: JwtService,
        private val configScheduler: ConfigScheduler,
        private val fileRepo: FileRepo
) {

    @GetMapping("/api/cloud/download/file/{fileID}")
    fun download(@PathVariable fileID: Int, @RequestParam(required = false) download: Boolean?, @RequestParam(required = false) key: String?, @RequestParam(required = false) txt: Boolean?): Any {

        val file = fileRepo.findByIdOrNull(fileID) ?: return "404"

        if(key != null) {
            // for office documents
            val data = jwtService.getData(key)
            if(data?.get("fileID") != fileID.toString()) {
                return "403"
            }
        } else {
            val user = Session.get().user ?: return "403"
            if(!fileService.hasAccessToFile(user, file))
                return "403"
        }

        val resource = fileService.loadFileAsResource(file.path) ?: return "404"

        if(txt == true) {
            val content = try {
                InputStreamReader(resource.inputStream).use { reader -> FileCopyUtils.copyToString(reader) }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/plain"))
                    .body(content)
        } else {
            return ResponseEntity.ok()
                    .contentLength(resource.contentLength())
                    .contentType(MediaType.parseMediaType(fileService.getMimeFromPath(file.path)))
                    .apply {
                        if(download == true)
                            header("Content-Disposition", "attachment; filename=\"" + file.generateFileName() + "\"")
                    }
                    .body(resource)
        }
    }

    @GetMapping("/api/images/{fileName}")
    fun image(@PathVariable fileName: String, request: HttpServletRequest): Any {

        // get file content
        val resource = configScheduler.get(Element.fileLocation)?.let { location ->
            fileService.loadFileAsResource("$location/$fileName")
        } ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).build<Void>()

        // check 304 not modified
        val lastModified = (resource.lastModified() / 1000) * 1000 // ignore millis
        if(lastModified <= request.getDateHeader("If-Modified-Since")) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(HttpHeaders().apply {
                setDate("Last-Modified", lastModified)
            }).build<Void>()
        }

        // mime
        val mime = fileService.getMime(fileName.substring(fileName.lastIndexOf('.')+1).toLowerCase())
        if(!fileService.isImage(mime)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()

        // success request
        return ResponseEntity.ok()
                .headers(HttpHeaders().apply {
                    setDate("Last-Modified", lastModified)
                })
                .cacheControl(CacheControl.empty().cachePublic())
                .contentLength(resource.contentLength())
                .contentType(MediaType.parseMediaType(mime))
                .body(resource)
    }
}