package com.yupi.yupicturebackend.api.imagesearch.baidu.sub;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.api.imagesearch.baidu.model.ImageSearchResult;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GetImageListApi {

    /**
     * 获取图片列表 (第三步)
     *
     * @param url
     * @return
     */
    public static List<ImageSearchResult> getImageList(String url) {
        try {
            // 使用 Jsoup 发送 GET 请求，保持与 GetImagePageUrlApi 一致的访问方式
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            // 将 HTML 文本传入统一的解析逻辑
            String body = document.outerHtml();
            return processResponse(body);
        } catch (Exception e) {
            log.error("获取图片列表失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败");
        }
    }

    /**
     * 处理接口响应内容
     * 支持两种格式：
     * 1. 百度 JSON 格式：data.list 结构
     * 2. Bing HTML 格式：HTML 页面中的图片元素
     *
     * @param responseBody 接口返回的内容（JSON 或 HTML）
     */
    private static List<ImageSearchResult> processResponse(String responseBody) {
        // ========== 原百度 JSON 处理（已注释）==========
        /*
        JSONObject jsonObject = new JSONObject(responseBody);
        if (!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONObject data = jsonObject.getJSONObject("data");
        if (!data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list, ImageSearchResult.class);
        */

        // ========== 新 Bing HTML 处理==========
        // 尝试先作为 JSON 解析（百度格式），失败则作为 HTML 解析（Bing 格式）
        try {
            // 尝试百度 JSON 格式
            JSONObject jsonObject = JSONUtil.parseObj(responseBody);
            if (jsonObject.containsKey("data")) {
                JSONObject data = jsonObject.getJSONObject("data");
                if (data.containsKey("list")) {
                    JSONArray list = data.getJSONArray("list");
                    return JSONUtil.toList(list, ImageSearchResult.class);
                }
            }
        } catch (Exception e) {
            log.debug("尝试 JSON 解析失败，改为 HTML 解析: {}", e.getMessage());
        }

        // 作为 HTML 解析（Bing 搜图结果页面）
        try {
            Document document = Jsoup.parse(responseBody);
            List<ImageSearchResult> imageList = new ArrayList<>();

            // 策略 1：尝试从 img 标签的 src 属性提取
            Elements imgElements = document.select("img[src]");
            for (Element img : imgElements) {
                String src = img.attr("src");
                String alt = img.attr("alt");

                // 跳过空的或非图片的 URL
                if (src == null || src.isEmpty() || src.startsWith("data:")) {
                    continue;
                }

                // 创建图片搜索结果对象
                ImageSearchResult result = new ImageSearchResult();
                result.setThumbUrl(src);
                result.setFromUrl(src);
                result.setTitle(alt != null ? alt : "");

                imageList.add(result);

                if (imageList.size() >= 30) {
                    break;
                }
            }

            // 策略 2：如果 src 没有获取到足够的图片，尝试从 data-src 属性提取（lazy loading）
            if (imageList.size() < 10) {
                Elements lazyImgElements = document.select("img[data-src]");
                for (Element img : lazyImgElements) {
                    String dataSrc = img.attr("data-src");
                    String alt = img.attr("alt");

                    if (dataSrc == null || dataSrc.isEmpty() || dataSrc.startsWith("data:")) {
                        continue;
                    }

                    ImageSearchResult result = new ImageSearchResult();
                    result.setThumbUrl(dataSrc);
                    result.setFromUrl(dataSrc);
                    result.setTitle(alt != null ? alt : "");

                    imageList.add(result);

                    if (imageList.size() >= 30) {
                        break;
                    }
                }
            }

            // 策略 3：如果还是没有获取到，尝试从 a 标签的 href 提取（某些搜图页面）
            if (imageList.size() < 10) {
                Elements linkElements = document.select("a[href*=image]");
                for (Element link : linkElements) {
                    String href = link.attr("href");
                    String text = link.text();

                    if (href == null || href.isEmpty()) {
                        continue;
                    }

                    ImageSearchResult result = new ImageSearchResult();
                    result.setThumbUrl(href);
                    result.setFromUrl(href);
                    result.setTitle(text != null ? text : "");

                    imageList.add(result);

                    if (imageList.size() >= 30) {
                        break;
                    }
                }
            }

            if (imageList.isEmpty()) {
                log.warn("HTML 解析未获取到任何图片，响应体长度: {}", responseBody.length());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
            }

            log.info("从 Bing 搜图结果页面提取到 {} 张图片", imageList.size());
            return imageList;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("HTML 解析失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
    }

    public static void main(String[] args) {
        String url = "https://graph.baidu.com/ajax/pcsimi?carousel=503&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&inspire=general_pc&limit=30&next=2&render_type=card&session_id=16250747570487381669&sign=1265ce97cd54acd88139901733452612&tk=4caaa&tpl_from=pc";
        List<ImageSearchResult> imageList = getImageList(url);
        System.out.println("搜索成功" + imageList);
    }
}
