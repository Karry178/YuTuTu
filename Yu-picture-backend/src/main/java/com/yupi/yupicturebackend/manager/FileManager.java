package com.yupi.yupicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * manager包：主要存放可以服用的代码，不止是与这个项目绑定，而是可以直接复用到其余项目中
 * 直接把manager包复制搬运到其余项目中即可
 * <p>
 * 这个FileManager类更多的功能是实现业务！
 * 如对图片校验、图片上传的路径以及解析图片内容(需要使用腾讯云数据万象服务)
 */
@Slf4j
@Service
public class FileManager {

    // 引入CosClient的配置类
    @Resource
    private CosClientConfig cosClientConfig;

    // 引入CosManager
    @Resource
    private CosManager cosManager;


    /**
     * 上传图片服务
     *
     * @param multipartFile    上传的文件
     * @param uploadPathPrefix 上传的路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1.校验图片，调用校验方法
        validPicture(multipartFile);
        // 2.图片上传地址
        // 每一个文件前面要加一个UUID前缀，防止修改图片名字重复，UUID全随机
        String uuid = RandomUtil.randomString(16);
        // 获取原始文件名
        String originalFilename = multipartFile.getOriginalFilename();
        // 给文件名最后加一个时间前缀,文件名 = 时间前缀 + UUID + 原始文件名， 增加安全性
        String uploadFilename = String.format("%s_%s.%s", DateUtils.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originalFilename));

        // ** 如果多个项目使用共同的存储桶，需要加单独的一步：给每个项目加单独的前缀，方便区分
        // String projectName = "yu-picture"; // 加在"/%s/%s"前面

        // 最终的文件路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix);
        // 3.解析结果并返回
        // 把 filepath和后缀 转为 multipartFile 格式
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(uploadPath, null);
            // 把上传的file文件转为multipartFile格式 + filepath 传给cosManager
            // 但是上面把值传给file只是在本地创建了一个临时文件，前端上传的文件并未转到本地，需要调用multipartFile的transferTo方法把multipartFile文件传输到本地存储文件中
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息返回对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            // 计算宽高比：先用NumberUtil的round方法四舍五入计算的值，并保留2位小数，最后通过doubleValue方法取出值
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

            // 封装返回结果,新建一个UploadPictureResult对象，获取全部值
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(imageInfo.getWidth());
            uploadPictureResult.setPicHeight(imageInfo.getHeight());
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());

            // 最后，返回可访问的地址
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 4.临时文件清理
            deleteTempFile(file);
        }

    }


    /**
     * 校验文件
     *
     * @param multipartFile 上传的文件
     */
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1.校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024; // 定义1M大小
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2M");
        // 2.校验文件格式/后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 定义允许上传的文件后缀列表/集合
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件格式错误");
    }


    /**
     * 删除临时文件
     *
     * @param file
     */
    private void deleteTempFile(File file) {
        // 因为上传到云端需要先上传到本地，上传云端成功后，需要删除上传到本地文件的内容，不然冗余了
        if (file != null) {
            // 删除临时文件
            boolean delete = file.delete();
            if (!delete) {
                log.error("file delete error, filepath = {}", file.getAbsoluteFile());
            }
        }
    }
}
