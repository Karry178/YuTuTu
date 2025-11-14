package com.yupi.yupicturebackend.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    // 引入CosManager，方便把上传的对象引入对象存储
    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传接口：仅管理员有权限访问
     *
     * @param multipartFile 文件
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 定义文件目录和临时的文件名
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);

        // 把 filepath和后缀 转为 multipartFile 格式
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            // 把上传的file文件转为multipartFile格式 + filepath 传给cosManager
            // 但是上面把值传给file只是在本地创建了一个临时文件，前端上传的文件并未转到本地，需要调用multipartFile的transferTo方法把multipartFile文件传输到本地存储文件中
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 最后，返回可访问的地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("文件上传错误，filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 因为上传到云端需要先上传到本地，上传云端成功后，需要删除上传到本地文件的内容，不然冗余了
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }


    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {

        COSObjectInputStream cosObjectInput = null;

        try {
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            // 刷新缓冲区
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("文件下载错误，filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            // 关闭/释放流
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }

}
