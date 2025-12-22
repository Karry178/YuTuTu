package com.yupi.yupicturebackend.api.imagesearch.baidu.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;

/**
 * 获取以图搜图页面地址（步骤1）
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取以图搜图页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        // ========== 百度搜图代码（已注释，改用 Bing 搜图）==========
        /*
        // 百度表单参数
        Map<String, Object> formData = new HashMap<>();
        String encodedImageUrl = URLUtil.encode(imageUrl, StandardCharsets.UTF_8);
        formData.put("image", "image:" + encodedImageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        long uptime = System.currentTimeMillis();
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        String acsToken = "..."; // token 快速过期，已弃用
        String cookie = "..."; // cookie 快速过期，已弃用
        */
        // ========== Bing 搜图代码（新增）==========
        // Bing 不需要 token，只需要 URL 参数和 User-Agent，更稳定

        try {
            // ========== 百度搜图请求代码（已注释）==========
            /*
            HttpResponse httpResponse = HttpRequest.post(url)
                    .header("Acs-Token", acsToken)
                    .header("cookie", cookie)
                    // ... 其他百度请求头 ...
                    .form(formData)
                    .timeout(5000)
                    .execute();
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            String body = httpResponse.body();
            Map<String, Object> result = JSONUtil.toBean(body, Map.class);
            if (result == null || Convert.toInt(result.get("status"), -1) != 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(searchResultUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效的结果地址");
            }
            return searchResultUrl;
            */
            // ========== Bing 搜图请求代码（新增）==========
            // 1. 构造 Bing 搜图 URL（无需 token，只需 URL 参数）
            String encodedImageUrl = URLUtil.encode(imageUrl, StandardCharsets.UTF_8);
            String bingSearchUrl =
                    "https://www.bing.com/images/search?view=detailv2&iss=sbi"
                            + "&FORM=SBIVSP&sbisrc=ImgDropper"
                            + "&imgurl=" + encodedImageUrl;

            // 2. 验证搜图页面是否可访问（简单验证）
            Document document = Jsoup.connect(bingSearchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            // 3. 检查是否真的返回了搜图结果（通过检查是否有图片元素）
            // Bing 搜图结果通常在 img.mimg 或类似的标签中
            if (document.select("img").isEmpty()) {
                log.warn("Bing 搜图未找到结果，imageUrl: {}", imageUrl);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效的结果地址");
            }

            // 4. 返回 Bing 搜图结果页面 URL（而不是第一个结果的链接）
            // 这样后续可以从这个页面提取多张图片
            log.info("Bing 搜图成功，结果页面 URL: {}", bingSearchUrl);
            return bingSearchUrl;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("调用 Bing 以图搜图接口失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }


    /**
     * 测试以图搜图
     *
     * @param args
     */
    public static void main(String[] args) {
        String imageUrl = "https://www.codefather.cn/logo.png";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + searchResultUrl);
    }
}
