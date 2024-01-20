package com.javdc.cup;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;

public class CupApplication {

	public static void main(String[] args) {
		/* SpringApplication.run(CupApplication.class, args); */

		try {

			String scanPath = "D:\\tinyMediaManager\\media\\cctv";
			String outputPath = "D:\\tinyMediaManager\\media\\cctv\\JAV_output";

			File file = new File(scanPath);
			if (!file.exists()) {
				return;
			}

			File destPath = FileUtil.file(outputPath);
			if (!destPath.exists()) {
				destPath.mkdirs();
			}

			String[] fileNames = file.list(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					String mimeType = FileUtil.getMimeType(name);
					if (("video/mp4").equals(mimeType)) {
						return true;
					}
					return false;
				}
			});

			System.out.println("扫描到视频文件：" + Arrays.toString(fileNames));

			if (fileNames.length == 0) {
				return;
			}

			for (String fileName : fileNames) {
				File movieFile = new File(scanPath + File.separator + fileName);

				Map<String, String> infoMap = new HashMap<>();
				boolean hasMovie = search(fileName, infoMap);

				if (!hasMovie) {
					System.out.println("没找到视频");
					continue;
				}

				String website = infoMap.get("website");
				downloadMovieInfo(infoMap, movieFile, destPath);

				/*
				 * File moviePath = destStarFile.getAbsolutePath()+File.pathSeparator+
				 * FileUtil.move(movieFile, destStarFile, false);
				 */

			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.out.println(DateUtil.date());
		}

	}

	private static void downloadMovieInfo(Map<String, String> infoMap, File movieFile, File destPath) {
		String url = infoMap.get("website");
		HttpRequest request = HttpUtil.createGet(url, true)
				.contentType("text/html;Charset=utf-8;;charset=UTF-8");
		Map<String, String> headers = new HashMap<>();
		headers.put("Accept-Encoding", "gzip, deflate");
		headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
		headers.put("Cache-Control", "no-cache");
		headers.put("Pragma", "no-cache");
		headers.put("Referer", "https://www.javbus.com/?ref=porndude");
		headers.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0");
		headers.put("Sec-Fetch-User", "?1");
		headers.put("Upgrade-Insecure-Requests", "1");
		request.addHeaders(headers);
		request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809)));
		request.setReadTimeout(-1);

		HttpResponse response = request.execute();
		if (response.getStatus() != 200) {
			return;
		}
		byte[] body = response.bodyBytes();

		String docString;
		try {
			docString = new String(body, response.charset());
			Document document = Jsoup.parse(docString);

			String title = "", originaltitle = "", sorttitle = "", set = "", studio = "", year = "", outline = "",
					plot = "", director = "", poster = "poster.jpg", thumb = "thumb.jpg",
					fanart = "fanart.jpg", maker = "", label = "", num = "", premiered = "", releasedate = "",
					release = "", cover = "", website = "";
			List<String> actor = new ArrayList<>();
			List<String> tags = new ArrayList<>();
			List<String> genres = new ArrayList<>();

			Elements actorEles = document.select("div.star-name");
			String dirName = "";
			if (actorEles.size() > 1) {
				dirName = "多人作品";
			}
			if (actorEles.size() == 1) {
				dirName = actorEles.get(0).text();
			}

			System.out.println(dirName);
			File destStarFile = new File(destPath + File.separator + dirName);
			if (!destStarFile.exists()) {
				destStarFile.mkdirs();
			}

			Element numEle = document.select("div.movie>div.info>p").first();
			num = numEle.select("span").get(1).text();

			File destMoviePath = new File(destStarFile.getAbsolutePath() + File.separator + num);
			if (destMoviePath.exists()) {
				System.out.println("视频已存在");
				return;
			} else {
				destMoviePath.mkdirs();
			}

			// 移动视频文件
			FileUtil.move(movieFile, destMoviePath, false);

			// 下载thumb
			Elements thumbEles = document.select(".screencap img");
			if (thumbEles.size() > 0) {
				Element thumbEle = thumbEles.get(0);
				String thumbSrc = thumbEle.attr("src");

				File thumbFile = new File(destMoviePath + File.separator + "thumb.jpg");
				String thumbUrl = thumbSrc;
				if (!thumbUrl.startsWith("http") && !thumbUrl.startsWith("https")) {
					thumbUrl = "https://www.javbus.com/" + thumbUrl;
				}
				System.out.println("thumb地址：" + thumbUrl);
				downloadFile(thumbUrl, thumbFile);

				FileUtil.copy(thumbFile, new File(destMoviePath.getAbsolutePath() + File.separator + "fanart.jpg"),
						false);
			}

			// 下载cover
			String coverPath = infoMap.get("coverPath");
			if (!coverPath.startsWith("http") && !coverPath.startsWith("https")) {
				coverPath = "https://www.javbus.com/" + coverPath;
			}
			File thumbFile = new File(destMoviePath + File.separator + "poster.jpg");
			downloadFile(coverPath, thumbFile);

			// 下载extrafanart
			Elements extrafanartEles = document.select(".sample-box");
			if (extrafanartEles.size() > 0) {
				File extrefanartPath = new File(destMoviePath + File.separator + "extrafanart");
				if (!extrefanartPath.exists()) {
					extrefanartPath.mkdirs();
				}
				int index = 1;
				for (Element element : extrafanartEles) {
					Elements imgs = element.select("img");
					for (Element img : imgs) {
						String imgUrl = img.attr("src");
						if (!imgUrl.startsWith("http") && !imgUrl.startsWith("https")) {
							imgUrl = "https://www.javbus.com/" + imgUrl;
						}
						File dImg = new File(extrefanartPath + File.separator + "extrafanart-" + index + ".jpg");
						downloadFile(imgUrl, dImg);
						index++;
					}
				}
			}

			// 生成nfo文件
			createNfoFile(document, destMoviePath);
		} catch (Exception e) {

		}
	}

	private static void createNfoFile(Document document, File destMoviePath) {
		Element titleEle = document.selectFirst("title");
		Elements infoEles = document.select("div.movie>div.info>p");

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = factory.newDocumentBuilder();
			org.w3c.dom.Document xmlDocument = db.newDocument();
			org.w3c.dom.Element movieElement = xmlDocument.createElement("movie");
			xmlDocument.appendChild(movieElement);

			org.w3c.dom.Element titleElemet = xmlDocument.createElement("title");
			titleElemet.setTextContent(titleEle.text());
			movieElement.appendChild(titleElemet);

			org.w3c.dom.Element originaltitleElemet = xmlDocument.createElement("originaltitle");
			originaltitleElemet.setTextContent(titleEle.text());
			movieElement.appendChild(originaltitleElemet);

			org.w3c.dom.Element sorttitleElemet = xmlDocument.createElement("sorttitle");
			sorttitleElemet.setTextContent(titleEle.text());
			movieElement.appendChild(sorttitleElemet);

			org.w3c.dom.Element posterElemet = xmlDocument.createElement("poster");
			posterElemet.setTextContent("poster.jpg");
			org.w3c.dom.Element thumbElemet = xmlDocument.createElement("thumb");
			thumbElemet.setTextContent("thumb.jpg");
			org.w3c.dom.Element fanartElemet = xmlDocument.createElement("fanart");
			fanartElemet.setTextContent("fanart.jpg");
			movieElement.appendChild(posterElemet);
			movieElement.appendChild(thumbElemet);
			movieElement.appendChild(fanartElemet);

			for (int i = 0; i < infoEles.size(); i++) {
				Element e = infoEles.get(i);
				Element header = e.selectFirst("span.header");
				if (header == null) {
					// 查找上一个
					Element be = infoEles.get(i - 1);
					Element beHeader = be.selectFirst("span.header");
					if(beHeader == null){
						Elements tags = e.select("span.genre");
						for (Element element : tags) {
							org.w3c.dom.Element ele = xmlDocument.createElement("tag");
							ele.setTextContent(element.text());
							movieElement.appendChild(ele);
						}
					}else{
						Elements tags = e.select("span.genre");
						for (Element element : tags) {
							org.w3c.dom.Element ele = xmlDocument.createElement("actor");
							org.w3c.dom.Element actName = xmlDocument.createElement("name");
							actName.setTextContent(element.text());
							ele.appendChild(actName);
							movieElement.appendChild(ele);
						}
					}
					continue;
				}
				String fieldName = header.text();
				if (fieldName.contains("識別碼")) {
					String num = e.select("span").get(1).text();
					org.w3c.dom.Element ele = xmlDocument.createElement("num");
					ele.setTextContent(num);
					movieElement.appendChild(ele);
					continue;
				}
				if (fieldName.contains("系列")) {
					String text = e.select("a").get(0).text();
					org.w3c.dom.Element ele = xmlDocument.createElement("set");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					continue;
				}
				if (fieldName.contains("發行日期")) {
					String text = e.text();
					org.w3c.dom.Element ele = xmlDocument.createElement("releasedate");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					org.w3c.dom.Element ele1 = xmlDocument.createElement("premiered");
					ele1.setTextContent(text);
					movieElement.appendChild(ele1);
					org.w3c.dom.Element ele2 = xmlDocument.createElement("release");
					ele2.setTextContent(text);
					movieElement.appendChild(ele2);
					continue;
				}
				if (fieldName.contains("長度")) {
					String text = e.text();
					org.w3c.dom.Element ele = xmlDocument.createElement("runtime");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					continue;
				}
				if (fieldName.contains("導演")) {
					String text = e.select("a").get(0).text();
					org.w3c.dom.Element ele = xmlDocument.createElement("director");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					continue;
				}
				if (fieldName.contains("製作商")) {
					String text = e.select("a").get(0).text();
					org.w3c.dom.Element ele = xmlDocument.createElement("studio");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					org.w3c.dom.Element ele1 = xmlDocument.createElement("maker");
					ele1.setTextContent(text);
					movieElement.appendChild(ele1);
					continue;
				}
				if (fieldName.contains("系列")) {
					String text = e.select("a").get(0).text();
					org.w3c.dom.Element ele = xmlDocument.createElement("set");
					ele.setTextContent(text);
					movieElement.appendChild(ele);
					continue;
				}
			}

			// 创建TransformerFactory对象
			TransformerFactory tff = TransformerFactory.newInstance();
			// 创建 Transformer对象
			Transformer tf = tff.newTransformer();

			// 输出内容是否使用换行
			tf.setOutputProperty(OutputKeys.INDENT, "yes");
			// 创建xml文件并写入内容
			tf.transform(new DOMSource(movieElement),
					new StreamResult(new File(destMoviePath + File.separator + destMoviePath.getName()+".nfo")));

		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println(e.getStackTrace());
		} 
	}

	private static boolean search(String fileName, Map<String, String> infoMap) {
		String url = "https://www.javbus.com/search/" + fileName.substring(0, fileName.lastIndexOf("."));
		HttpRequest request = HttpUtil.createGet(url, true)
				.contentType("text/html;Charset=utf-8;;charset=UTF-8");
		Map<String, String> headers = new HashMap<>();
		headers.put("Accept-Encoding", "gzip, deflate");
		headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
		headers.put("Cache-Control", "no-cache");
		headers.put("Pragma", "no-cache");
		headers.put("Referer", "https://www.javbus.com/?ref=porndude");
		headers.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0");
		headers.put("Sec-Fetch-User", "?1");
		headers.put("Upgrade-Insecure-Requests", "1");
		request.addHeaders(headers);
		HttpRequest.setGlobalTimeout(-1);
		request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809)));
		request.setReadTimeout(-1);

		HttpResponse response = request.execute();
		byte[] body = response.bodyBytes();

		String docString;
		try {
			docString = new String(body, response.charset());
			Document document = Jsoup.parse(docString);

			Elements title = document.select("title");
			if (title.size() > 0) {
				Element tElement = title.get(0);
				if (tElement.text().contains("沒有您要的結果")) {
					return false;
				}

				Elements movieEles = document.select(".movie-box");
				if (movieEles.size() == 0) {
					return false;
				}

				Element movieEle = movieEles.get(0);
				String detailUrl = movieEle.attr("href");
				infoMap.put("website", detailUrl);

				Elements coverEles = movieEle.select("img");
				if (coverEles.size() > 0) {
					infoMap.put("coverPath", coverEles.get(0).attr("src"));
				}

				return true;
			}
			return false;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private static void downloadFile(String url, File thumbFile) {
		HttpRequest request = HttpUtil.createGet(url, true);
		request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809)));
		request.setReadTimeout(-1);
		HttpResponse response = request.execute();
		InputStream is = response.bodyStream();
		FileUtil.writeFromStream(is, thumbFile.getAbsolutePath());
	}

}
