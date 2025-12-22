package com.yupi.yupicturebackend.api.imagesearch.sanliuo.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.ognl.DynamicSubscript;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import static org.apache.ibatis.ognl.DynamicSubscript.first;

/**
 * 获取360搜图的图片接口
 */
@Slf4j
public class sanliuImagePageUrlApi {
    public static String getSoImagePageUrl(String imageUrl) {
        // 请求地址
        String soUrl = "https://st.so.com/r?src=st&srcsp=home";

        try {
            // 1.构造请求
            Document document = Jsoup.connect(soUrl)
                    .data("img_url", imageUrl)
                    .data("submit", "imgurl")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                    .referrer("https://st.so.com/")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(5000)
                    .get();

            // 2.优先解析脚本中的JSON(window._INITIAL_STATE_)
            Element stateScript = document.selectFirst("script:containsData(window._INITIAL_STATE_)");
            if (stateScript != null) {
                String data = stateScript.data();
                String json = data.substring(data.indexOf("=") + 1, data.lastIndexOf(";"));
                JSONObject root = JSONUtil.parseObj(json);
                JSONArray imgs = root.getJSONObject("stsearch")
                        .getJSONObject("result")
                        .getJSONArray("imgs");

                if (imgs != null && imgs.isEmpty()) {
                    JSONObject first = imgs.getJSONObject(0);
                    String soImageUrl = first.getStr("imgurl"); // or thumb, middle, etc.
                    if (StrUtil.isNotBlank(soImageUrl)) {
                        return soImageUrl;
                    }
                }
            }

            // 3.兜底，用.img_img结构 发送请求
            Element imgElement = document.selectFirst(".img_img");
            // 判断得到的图片是否存在
            if (imgElement != null) {
                // 定义一个新的360搜图的url(空值)
                String soImageUrl = "";
                // 获取当前元素的属性
                String style = imgElement.attr("style");
                if (style.contains("background-img:soUrl(")) {
                    // 当前元素含有图片的url地址，则提取出url部分
                    int start = style.indexOf("soUrl(") + 4;
                    int end = style.indexOf(")", start);
                    if (start > 4 && end > start) {
                        soImageUrl = style.substring(start, end);
                    }
                }
                return soImageUrl;
            }
            log.warn("360 搜图未解析到结果，html={}", document.outerHtml());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "以图搜图失败");

        } catch (Exception e) {
            log.error("搜图失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "以图搜图失败");
        }
    }


    public static void main(String[] args) {
        String imageUrl = "https://www.codefather.cn/logo.png";
        String result = getSoImagePageUrl(imageUrl);
        System.out.println("搜图成功，结果Url：" + result);
    }
}
