package com.yupi.yupicturebackend.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureTagCategory;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    // 引入UserService
    @Resource
    private UserService userService;

    // 引入PictureService
    @Resource
    private PictureService pictureService;


    /**
     * 【增】通过文件上传图片(可重新上传，因为业务层中定义文件名加了前缀，前缀一定不同)
     *
     * @param multipartFile        前端传来的文件
     * @param pictureUploadRequest 用户上传图片请求
     * @param request              登录请求
     * @return
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletRequest request) {
        // 1.获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 2.调用pictureService的上传图片方法
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);

        // 3.返回给前端的图片信息
        return ResultUtils.success(pictureVO);
    }


    /**
     * 【增】通过URL上传图片(可重新上传，因为业务层中定义文件名加了前缀，前缀一定不同)
     *
     * @param pictureUploadRequest 用户上传图片请求
     * @param request              登录请求
     * @return
     */
    @PostMapping("/upload/url")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        // 1.获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 2.获取文件的url
        String fileUrl = pictureUploadRequest.getFileUrl();

        // 3.调用pictureService的上传图片方法
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);

        // 4.返回给前端的图片信息
        return ResultUtils.success(pictureVO);
    }


    /**
     * 【删】删除图片
     * @param deleteRequest 删除的通用请求，使用id删除
     * @return
     */
    @PostMapping("/delete")
    //  @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) 删除图片是本人和管理员均可，不要加AOP切面
    public BaseResponse<Boolean> deletePic(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 1.校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        // 2.判断用户是否可以删除图片
        // 获取登录用户信息
        User loginUser = userService.getLoginUser(request);
        // 再判断图片是否存在，通过图片id获取图片
        Long id = deleteRequest.getId();

        // 3.从数据库通过id获取图片 -> 如果图片存在，定义为老图片;不存在就报错
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4.图片存在了 -> 只有本人和管理员可以删除本张图片
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你无权删除本图片");
        }

        // 5.最后一步：操作数据库，删除图片
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 【改-管理员编辑图片信息】修改图片信息，管理员可以修改全部的图片信息，普通用户也不能更新自己的
     * @param pictureUpdateRequest 图片修改请求
     * @param request 登录用户
     * @return 经过VO封装后的pictureVO
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        // 1.校验
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.在此处将实体类和DTO进行转换 -> 把DTO的请求类对象赋值到实体类上
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);

        // 注意将list转为String -> 因为Picture的tags是字符串类型，而图片更新请求的tags是list
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        // 4.数据校验
          // 可以直接调用抽象类：validPicture 校验方法
        pictureService.validPicture(picture);

        // 5.判断图片是否存在
        Long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 补充审核参数 - 调用fillReviewParams方法
        pictureService.fillReviewParams(picture, loginUser);

        // 6.最后操作数据库，更新数据
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 【改-修改图片信息】 普通用户通过图片id修改图片信息
     * @param pictureEditRequest 图片编辑请求
     * @param request 登录用户
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        // 1.校验 - 检查图片编辑请求和其中的图片id是否存在
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.在此处进行实体类和DTO请求类的转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
          // 注意将list转为String
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
          // 设置编辑时间
        picture.setEditTime(new Date());

        // 3.数据校验
          // 调用图片类通用的方法validPicture
        pictureService.validPicture(picture);
          // 获取登录用户，并确保要编辑的图片真的存在(用id在修改图片请求中获取)
        User loginUser = userService.getLoginUser(request);
        // 补充审核参数 - 调用fillReviewParams方法
        pictureService.fillReviewParams(picture, loginUser);

        Long id = pictureEditRequest.getId();
          // 从pictureService中通过id查到图片的话，就叫oldPicture
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4.只有本人和管理员可以编辑查到的图片信息
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5.操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 【查】根据id获取图片(仅管理员可用)
     * @param id 图片id
     * @param request 登录用户
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        // 1.校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2.查询数据库 -> 根据id获取图片
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3.如果查找到了，获取封装类
        return ResultUtils.success(picture);
    }

    // 【查】根据id获取图片(封装VO类)
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2.查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3.如果查找到了，获取封装VO类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }


    /**
     * 【分页查询】分页获取图片列表（仅管理员）
     * @param pictureQueryRequest 图片分页请求
     * @return 分页后的picturePage
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        // 1.先获取当前页和每页最大列数 -> 从PageRequest中获取的
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2.限制爬虫 -> 获取页数请求大于每页20列就报错
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3.查询数据库
          // todo Page方法是什么？我没写在pictureServiceImpl中啊。
          // 是否为系统自带的page方法，前面是page，后面是存放到page的数据？
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));

        // 4.返回分页后的page
        return ResultUtils.success(picturePage);
    }


    /**
     * 【分页查询】分页获取图片的列表(封装VO类，只给普通用户看到)
     * @param pictureQueryRequest 图片分页请求
     * @param request 登录用户
     * @return 封装VO的分页数据
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 1.先获取当前页和每页最大列数 -> 从PageRequest中获取的
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2.限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3.普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 4.查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));

        // 5.获取VO封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }


    /**
     * 主页显示标签的初始化
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门","搞笑","生活","高清","艺术","校园","背景","简历","创意");
        List<String> categoryList = Arrays.asList("模版","电商","表情包","素材","海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     * 审核图片(仅管理员可用)
     *
     * @param pictureReviewRequest 图片审核请求
     * @param request              登录请求
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.获取登录用户 - 调用UserService
        User loginUser = userService.getLoginUser(request);
        // 3.调用pictureService的doPictureReview方法
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 批量抓取并创建图片(仅管理员)
     * @param pictureUploadByBatchRequest 图片批量抓取请求
     * @param request 登录用户
     * @return
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 3.调用pictureService的uploadPictureByBatch方法
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }
}
