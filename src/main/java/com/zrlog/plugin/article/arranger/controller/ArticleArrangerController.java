package com.zrlog.plugin.article.arranger.controller;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.article.arranger.service.ArrangerHelper;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.type.ActionType;

import java.util.*;
import java.util.logging.Logger;

public class ArticleArrangerController {

    private static final Logger LOGGER = LoggerUtil.getLogger(ArticleArrangerController.class);

    private final IOSession session;
    private final MsgPacket requestPacket;
    private final HttpRequestInfo requestInfo;

    public ArticleArrangerController(IOSession session, MsgPacket requestPacket, HttpRequestInfo requestInfo) {
        this.session = session;
        this.requestPacket = requestPacket;
        this.requestInfo = requestInfo;
    }

    public void update() {
        if (Objects.isNull(requestInfo.getUserId()) || requestInfo.getUserId() <= 0) {
            session.responseHtml("/templates/403.ftl", new HashMap<>(), requestPacket.getMethodStr(), requestPacket.getMsgId());
            return;
        }
        session.sendMsg(new MsgPacket(requestInfo.simpleParam(), ContentType.JSON, MsgPacketStatus.SEND_REQUEST, IdUtil.getInt(), ActionType.SET_WEBSITE.name()), msgPacket -> {
            Map<String, Object> map = new HashMap<>();
            map.put("success", true);
            session.sendMsg(new MsgPacket(map, ContentType.JSON, MsgPacketStatus.RESPONSE_SUCCESS, requestPacket.getMsgId(), requestPacket.getMethodStr()));
            //更新缓存
            session.sendJsonMsg(new HashMap<>(), ActionType.REFRESH_CACHE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST);
        });
    }

    private static Map<String, Object> getWidgetWithRetry(IOSession session, String realUri, boolean staticHtml, List<String> groups, int retryCount) throws InterruptedException {
        try {
            return ArrangerHelper.getWidgetData(session, realUri, staticHtml, groups);
        } catch (Exception e) {
            if (retryCount < 0) {
                throw new RuntimeException(e);
            }
            Thread.sleep(2000);
            return getWidgetWithRetry(session, realUri, staticHtml, groups, retryCount - 1);
        }
    }

    public void widget() {
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put("key", "styleGlobal,groups,mainColor");
        session.sendJsonMsg(keyMap, ActionType.GET_WEBSITE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, msgPacket -> {
            Map map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
            String realUri = requestInfo.getUri().replace(".action", "").replace(".html", "");
            boolean staticHtml = requestInfo.getUri().endsWith(".html");
            try {
                Map<String, Object> data = getWidgetWithRetry(session, realUri, staticHtml, new ArrayList<>(), 5);
                data.put("styleGlobal", Objects.requireNonNullElse(map.get("styleGlobal"), ""));
                data.put("mainColor", Objects.requireNonNullElse(map.get("mainColor"), "#007BFF"));
                session.responseHtml("/templates/widget.ftl", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
            } catch (Exception e) {
                session.responseHtmlStr("<div></div>", requestPacket.getMethodStr(), requestPacket.getMsgId());
                LOGGER.warning("Render widget error " + e.getMessage());
            }
        });
    }

    public void index() {
        if (Objects.isNull(requestInfo.getUserId()) || requestInfo.getUserId() <= 0) {
            session.responseHtml("/templates/403.ftl", new HashMap<>(), requestPacket.getMethodStr(), requestPacket.getMsgId());
            return;
        }
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put("key", "styleGlobal,groups");
        session.sendJsonMsg(keyMap, ActionType.GET_WEBSITE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, msgPacket -> {
            Map map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
            Map<String, Object> data = new HashMap<>();
            data.put("theme", Objects.equals(requestInfo.getHeader().get("Dark-Mode"), "true") ? "dark" : "light");
            if (Objects.isNull(map.get("groups"))) {
                data.put("groups", new ArrayList<>());
            }
            data.put("styleGlobal", Objects.requireNonNullElse(map.get("styleGlobal"), ""));
            session.responseHtml("/templates/index.ftl", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
        });
    }
}