package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.DateUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
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
public abstract class PictureUploadTemplate {

    // 引入CosClient的配置类
    @Resource
    private CosClientConfig cosClientConfig;

    // 引入CosManager
    @Resource
    private CosManager cosManager;


    /**
     * 图片上传模板
     *
     * @param inputSource      上传的文件
     * @param uploadPathPrefix 上传的路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1.校验图片，调用校验方法
        validPicture(inputSource);
        // 2.图片上传地址
        // 每一个文件前面要加一个UUID前缀，防止修改图片名字重复，UUID全随机
        String uuid = RandomUtil.randomString(16);
        // 获取原始文件名
        String originalFilename = getOriginalFilename(inputSource);
        // 给文件名最后加一个时间前缀,文件名 = 时间前缀 + UUID + 原始文件名， 增加安全性
        String dateStr = DateUtils.formatDate(new Date()).replace(":", "-").replace(" ", "_");
        String uploadFilename = String.format("%s_%s.%s", dateStr, uuid, FileUtil.getSuffix(originalFilename));


        // ** 如果多个项目使用共同的存储桶，需要加单独的一步：给每个项目加单独的前缀，方便区分
        // String projectName = "yu-picture"; // 加在"/%s/%s"前面

        // 最终的文件路径
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);

        // 解析结果并返回
        // 把 filepath和后缀 转为 inputSource 格式
        File file = null;
        try {
            // 3.创建临时文件，获取文件到服务器
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            processFile(inputSource, file);

            // 4.上传图片到对象存储(腾讯云)
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 5.获取图片信息封装返回结果
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 6.获取到图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
              // 再从得到的processResults中获取 处理转化后的所有图片列表 -> 两个处理方式：压缩图与缩略图
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                // 如果值非空，取出第一条压缩后的文件信息
                CIObject compressedCiObject = objectList.get(0);
                // 再取出第二条结果：缩略图处理后的(只有存在时，才取出, 先给缩略图默认值为:压缩后的图片)
                CIObject thumbnailCiObject = compressedCiObject;
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装压缩图的返回结果
                return buildResult(originalFilename, compressedCiObject, thumbnailCiObject, imageInfo);
            }

            // 调用 封装返回结果的方法，返回可以访问的地址
            return buildResult(imageInfo, uploadPath, originalFilename, file);

        } catch (Exception e) {
            log.error("图片上传到对象存储失败，错误信息：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 4.临时文件清理
            deleteTempFile(file);
        }

    }


    /**
     * 校验文件
     *
     * @param inputSource 上传的文件
     */
    protected abstract void validPicture(Object inputSource);


    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource
     * @return
     */
    protected abstract String getOriginalFilename(Object inputSource);


    /**
     * 处理输入源并生成本地临时文件
     *
     * @param inputSource
     * @param file
     */
    protected abstract void processFile(Object inputSource, File file);


    /**
     * 压缩后的封装返回结果
     *
     * @param originalFilename   初始文件名
     * @param compressedCiObject 压缩后的对象
     * @param thumbnailCiObject  图片的缩略图
     * @param imageInfo 图片信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressedCiObject, CIObject thumbnailCiObject, ImageInfo imageInfo) {
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        // 计算宽高比：先用NumberUtil的round方法四舍五入计算的值，并保留2位小数，最后通过doubleValue方法取出值
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

        // 封装返回结果,新建一个UploadPictureResult对象，获取全部值
        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 1.设置压缩后的原图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey()); // 上传路径：从当前压缩上传成功的对象中取出Key
        uploadPictureResult.setName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());

        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());

        // 最后，返回可访问的地址
        return uploadPictureResult;
    }


    /**
     * 封装返回结果
     *
     * @param imageInfo        对象存储返回的图片信息
     * @param uploadPath       上传路径
     * @param originalFilename 原始文件名
     * @param file             文件
     * @return 返回可以访问的地址
     */
    private UploadPictureResult buildResult(ImageInfo imageInfo, String uploadPath, String originalFilename, File file) {
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
        uploadPictureResult.setPicColor(imageInfo.getAve()); // Ave就是图片主色调

        // 最后，返回可访问的地址
        return uploadPictureResult;
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
    
    


