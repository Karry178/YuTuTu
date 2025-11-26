package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 17832
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-11-21 11:34:16
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);


    /**
     * 校验空间
     *
     * @param space
     * @param add   是否为创建时校验
     */
    void validSpace(Space space, boolean add);


    /**
     * 获取空间包装类(单条)
     *
     * @param space   空间
     * @param request 登录用户
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);


    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);


    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest 空间查询请求
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);


    /**
     * 根据space对象当前的空间级别 给其填充 当前的最大容量和最大条数
     * @param space
     */
    public void fillSpaceBySpaceLevel(Space space);
}
