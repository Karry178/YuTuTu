package com.yupi.yupicturebackend.api.imagesearch.baidu;

import com.yupi.yupicturebackend.api.imagesearch.baidu.model.ImageSearchResult;
import com.yupi.yupicturebackend.api.imagesearch.baidu.sub.GetImagePageUrlApi;
import com.yupi.yupicturebackend.api.imagesearch.baidu.sub.GetImageListApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片 —— 门面模式
     * 原百度流程（3步）：GetImagePageUrlApi → GetImageFirstUrlApi → GetImageListApi
     * 新 Bing 流程（2步）：GetImagePageUrlApi（返回搜图结果页面 URL）→ GetImageListApi（从页面提取多张图片）
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // ========== 原百度流程（已注释）==========
        /*
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
        */

        // ========== 新 Bing 流程==========
        // 步骤 1：获取 Bing 搜图结果页面 URL
        String bingSearchResultUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        log.info("Bing 搜图成功，结果页面 URL: {}", bingSearchResultUrl);

        // 步骤 2：从搜图结果页面提取多张图片列表
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(bingSearchResultUrl);
        log.info("从 Bing 搜图结果页面提取到 {} 张图片", imageList != null ? imageList.size() : 0);

        return imageList;
    }


    public static void main(String[] args) {
        List<ImageSearchResult> imageList = searchImage("https://www.codefather.cn/logo.png");
        System.out.println("结果列表：" + imageList);
    }
}
