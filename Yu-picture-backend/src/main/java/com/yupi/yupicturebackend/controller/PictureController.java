package com.yupi.yupicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureTagCategory;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    // 引入Redis
    @Resource
    private StringRedisTemplate  stringRedisTemplate;

    // 使用本地缓存Caffeine
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024) // 分配一个初始容量
            .maximumSize(10_000L) // 最大10000条
            .expireAfterWrite(Duration.ofMinutes(5)) // 写缓存之后的过期时间（5分钟）
            .build();

    // 引入SpaceService
    @Resource
    private SpaceService spaceService;


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
          // 调用deletePicture方法
        pictureService.deletePicture(deleteRequest.getId(), loginUser);

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
          // 获取登录用户，并确保要编辑的图片真的存在(用id在修改图片请求中获取)
        User loginUser = userService.getLoginUser(request);

        // 2.调用pictureService的editPicture方法
        pictureService.editPicture(pictureEditRequest, loginUser);

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

        // 3.空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }

        // 4.如果查找到了，获取封装类
        return ResultUtils.success(picture);
    }


    /**
     * 【查】根据id获取图片(封装VO类)
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2.查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3.【新增】空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            // 私有图库，判断是否是该图库管理员
            User loginUser = userService.getLoginUser(request);
            // 调用PictureService的checkPictureAuth方法
            pictureService.checkPictureAuth(loginUser, picture);
        }

        // 4.如果查找到了，获取封装VO类
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
          // 是否为系统自带的page方法，前面是page，后面是存放到page的数据？
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
          // 将分页功能的当前页数、每页显示数量
        Page<Picture> resultPage = new Page<>(current, size, picturePage.getTotal());
        resultPage.setRecords(picturePage.getRecords());
        // 4.返回分页后的page
        return ResultUtils.success(resultPage);
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

        // 3.空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库：普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true); // 不传spaceId,所以只能查到公开图库
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Long loginUserId = loginUser.getId(); // 获取登录用户id
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUserId.equals(space.getUserId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        // 4.查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));

        // 5.获取VO封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }


    /**
     * 【分页查询】分页获取图片的列表(封装VO类，只给普通用户看到) -> 分级缓存(本地缓存 + Redis分布式缓存)
     * @param pictureQueryRequest 图片分页请求
     * @param request 登录用户
     * @return 封装VO的分页数据
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 1.先获取当前页和每页最大列数 -> 从PageRequest中获取的
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2.限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3.普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 【重要】4.先查缓存，再查询数据库！
        // 4.1 构建缓存的key -> 把查询请求通过JSONUtil转为JSON格式
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 使用DigestUtils把转为的JSON字符串使用MD5加密 -> 获取hashKey
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 最后，构建Redis的key -> 项目名 + 方法名 + hashKey
        String cacheKey = String.format("yupicture:listPictureVOByPage:%s", hashKey);

        // 4.2 先从本地缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，缓存结果
            Page<PictureVO> cachePage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachePage);
        }

        // 4.3 本地缓存未命中！查询Redis分布式缓存
        // 先拿到Redis的值，从获取的值中拿到需要的redisKey
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            // 缓存命中了！需要更新本地缓存，返回结果
            // 从缓存中取出结果，需要先反序列化，把cachedValue转为Page对象
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5.如果Redis未命中结果，就查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 把查到的图片分页结果经过VO封装
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 把在数据库中查到的结果写入Redis缓存，方便下次查询
        // 5.1 更新Redis缓存，把VO封装结果通过JSON工具类序列化，存入cacheValue
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 【※】设置缓存的过期时间:5 - 10分钟（目的是避免 缓存雪崩:多个缓存同一时间集体失效，加剧数据库压力。解决方法：过期时间避免同一时间）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300); // 300s + (0,300)s 的过期时间
        // 最后，设置缓存的key、Value和过期时间
        opsForValue.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
        // 5.2 更新本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 6.获取VO封装类
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 【分页查询】分页获取图片的列表(封装VO类，只给普通用户看到) -> 有Redis缓存
     * @param pictureQueryRequest 图片分页请求
     * @param request 登录用户
     * @return 封装VO的分页数据
     */
    @PostMapping("/list/page/vo/cache/redis")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithRedisCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 1.先获取当前页和每页最大列数 -> 从PageRequest中获取的
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2.限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3.普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 【重要】4.先查缓存，再查询数据库！
          // 4.1 构建缓存的key -> 把查询请求通过JSONUtil转为JSON格式
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
          // 使用DigestUtils把转为的JSON字符串使用MD5加密 -> 获取hashKey
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
          // 最后，构建Redis的key -> 项目名 + 方法名 + hashKey
        String redisKey = String.format("yupicture:listPictureVOByPage:%s", hashKey);

        // 4.2 操作Redis，从缓存中查询
          // 先拿到Redis的值，从获取的值中拿到需要的redisKey
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        String cachedValue = opsForValue.get(redisKey);
        if (cachedValue != null) {
            // 如果不为空，说明之前存入了缓存，所以现在缓存命中了，现在需要缓存结果
            // 从缓存中取出结果，需要先反序列化，把cachedValue转为Page对象
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5.如果Redis未命中结果，就查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
          // 把查到的图片分页结果经过VO封装
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 把在数据库中查到的结果写入Redis缓存，方便下次查询
          // 把VO封装结果通过JSON工具类序列化，存入cacheValue
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 【※】设置缓存的过期时间:5 - 10分钟（目的是避免 缓存雪崩:多个缓存同一时间集体失效，加剧数据库压力。解决方法：过期时间避免同一时间）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300); // 300s + (0,300)s 的过期时间
          // 最后，设置缓存的key、Value和过期时间
        opsForValue.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 6.获取VO封装类
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 【分页查询】分页获取图片的列表(封装VO类，只给普通用户看到) -> 有Caffeine本地缓存
     * @param pictureQueryRequest 图片分页请求
     * @param request 登录用户
     * @return 封装VO的分页数据
     */
    @PostMapping("/list/page/vo/cache/caffeine")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCaffeineCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 1.先获取当前页和每页最大列数 -> 从PageRequest中获取的
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2.限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 3.普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 【重要】4.先查缓存，再查询数据库！
        // 4.1 构建缓存的key -> 把查询请求通过JSONUtil转为JSON格式
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 使用DigestUtils把转为的JSON字符串使用MD5加密 -> 获取hashKey
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 最后，构建Redis的key -> 项目名 + 方法名 + hashKey
        String cacheKey = String.format("listPictureVOByPage:%s", hashKey);

        // 4.2 操作本地缓存Caffeine，从缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果不为空，说明之前存入了缓存，所以现在缓存命中了，现在需要缓存结果
            // 从缓存中取出结果，需要先反序列化，把cachedValue转为Page对象
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5.如果Redis未命中结果，就查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 把查到的图片分页结果经过VO封装
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 把在数据库中查到的结果写入Redis缓存，方便下次查询
        // 把VO封装结果通过JSON工具类序列化，存入cacheValue
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 【※】设置缓存的过期时间:5 - 10分钟（目的是避免 缓存雪崩:多个缓存同一时间集体失效，加剧数据库压力。解决方法：过期时间避免同一时间）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300); // 300s + (0,300)s 的过期时间
        // 写入本地缓存Caffeine
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 6.获取VO封装类
        return ResultUtils.success(pictureVOPage);
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
