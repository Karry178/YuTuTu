package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 17832
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-11-10 22:41:52
 */
public interface PictureService extends IService<Picture> {


    /**
     * 基本的图片数据校验
     * @param picture 上传的图
     */
    public void validPicture(Picture picture);


    /**
     * 登录用户-上传图片
     *
     * @param inputSource        前端传来的上传的图片文件/Url
     * @param pictureUploadRequest 用户上传图片请求体，里面只有id，方便修改图片
     * @param loginUser            登录用户-目的是判断用户有无权限上传图片
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);


    /**
     * 获取查询图片对象
     *
     * @param pictureQueryRequest 图片查询请求
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    /**
     * 获取单个图片的封装 - 返回VO类
     *
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);


    /**
     * 分页获取图片封装列表
     *
     * @param picturePage 分页
     * @param request     登录用户
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);


    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);


    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片请求
     * @param loginUser                   登录用户
     * @return 抓取的图片数量
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);


    /**
     * 清理图片文件
     * @param oldPicture 要清理的图片
     */
    void clearPictureFile(Picture oldPicture);


    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);


    /**
     * 校验空间图片的权限
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);


    /**
     * 【提取后】编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);
}
