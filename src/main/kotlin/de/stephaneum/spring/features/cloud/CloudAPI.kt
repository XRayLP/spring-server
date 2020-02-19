package de.stephaneum.spring.features.cloud

import de.stephaneum.spring.Session
import de.stephaneum.spring.database.*
import de.stephaneum.spring.helper.FileService
import de.stephaneum.spring.helper.ImageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths

@RestController
@RequestMapping("/api/cloud")
class CloudAPI {

    @Autowired
    private lateinit var fileService: FileService

    @Autowired
    private lateinit var imageService: ImageService

    @Autowired
    private lateinit var fileRepo: FileRepo

    @Autowired
    private lateinit var folderRepo: FolderRepo

    @GetMapping("/info")
    fun getInfo(): Any {
        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)

        val used = fileRepo.calcStorageUsed(user.id)
        val total = user.storage

        return Response.CloudInfo(used, total)
    }

    @GetMapping("/view/user/", "/view/user/{folder}")
    fun getCloudUser(@PathVariable(required = false) folder: Int?): Any {
        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)

        val folders: List<Folder>
        val files: List<File>
        if(folder != null) {
            val folderObj = Folder(folder)
            folders = folderRepo.findByParent(folderObj)
            files = fileRepo.findByUserAndFolderOrderByIdDesc(user, folderObj)
        } else {
            folders = folderRepo.findFolderInRoot(user, null, null, false)
            files = fileRepo.findByUserAndFolderOrderByIdDesc(user, null)
        }

        return digestResults(folders, files)
    }

    @PostMapping("/upload/user/", "/upload/user/{folder}")
    fun uploadImage(@PathVariable(required = false) folder: Int?, @RequestParam("file") file: MultipartFile): Any {

        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)
        var fileName = file.originalFilename ?: return Response.Feedback(false, message = "Dateiname unbekannt")
        var contentType = file.contentType ?: return Response.Feedback(false, message = "Dateityp unbekannt")

        // ensure that the image is rotated properly
        var bytes = file.bytes
        if(contentType.startsWith("image")) {
            val rotatedBytes = imageService.digestImageRotation(file.bytes)
            if (rotatedBytes != null) {
                bytes = rotatedBytes
                fileName = fileService.getPathWithNewExtension(fileName, "jpg")
                contentType = Files.probeContentType(Paths.get(fileName))
            }
        }

        val result = fileService.storeFileStephaneum(user, fileName, contentType, bytes, folder, -1, FileService.StoreMode.PRIVATE)
        if(result is File)
            result.simplifyForPosts()

        return if(result is String) Response.Feedback(false, message = result) else result
    }

    @PostMapping("/update-public-file")
    fun updatePublic(@RequestBody request: Request.UpdatePublic): Response.Feedback {

        if(request.fileID == null || request.isPublic == null)
            return Response.Feedback(false, message = "Missing Arguments")

        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)
        val file = fileRepo.findByIdOrNull(request.fileID) ?: return Response.Feedback(false, message = "file not found")

        if(!hasAccessToFile(user, file))
            return Response.Feedback(false, message = "no access to this file")

        file.public = request.isPublic
        fileRepo.save(file)

        return Response.Feedback(true)
    }

    @PostMapping("/delete-file/{fileID}")
    fun deleteFile(@PathVariable fileID: Int): Response.Feedback {

        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)
        val file = fileRepo.findByIdOrNull(fileID) ?: return Response.Feedback(false, message = "file not found")

        if(!hasAccessToFile(user, file))
            return Response.Feedback(false, message = "no access to this file")

        fileRepo.delete(file)

        return Response.Feedback(true)
    }

    @PostMapping("/move-file")
    fun moveFile(@RequestBody request: Request.MoveFile): Response.Feedback {

        if(request.fileID == null || request.parentFolderID == null)
            return Response.Feedback(false, message = "Missing Arguments")

        val user = Session.get().user ?: return Response.Feedback(false, needLogin = true)
        val file = fileRepo.findByIdOrNull(request.fileID) ?: return Response.Feedback(false, message = "file not found")
        val folder = folderRepo.findByIdOrNull(request.parentFolderID) ?: return Response.Feedback(false, message = "file not found")

        if(!hasAccessToFile(user, file))
            return Response.Feedback(false, message = "no access to this file")

        file.folder = folder
        fileRepo.save(file)

        return Response.Feedback(true)
    }

    private fun hasAccessToFile(user: User, file: File): Boolean {
        return when {
            file.user?.id == user.id -> true // user owns this file
            user.code.role == ROLE_ADMIN -> true // user is admin
            else -> false
        }
    }

    private fun calcSizeRecursive(folder: Folder): Int {
        val fileSize = fileRepo.findByFolderOrderByIdDesc(folder).sumBy { it.size }
        folder.size = fileSize
        folderRepo.findByParent(folder).forEach { f ->
            folder.size += calcSizeRecursive(f)
        }
        return folder.size
    }

    private fun digestResults(folders: List<Folder>, files: List<File>): List<Any> {
        for (f in folders) {
            f.simplifyForCloud()
            calcSizeRecursive(f)
        }
        files.forEach { f -> f.simplifyForCloud() }

        return List<Any>(folders.size + files.size) { i ->
            if(i < folders.size)
                folders[i]
            else
                files[i-folders.size]
        }
    }

}