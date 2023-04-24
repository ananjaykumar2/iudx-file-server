package iudx.file.server.common.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import iudx.file.server.common.ServerType;
import iudx.file.server.common.WebClientFactory;
import iudx.file.server.common.service.CatalogueService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static iudx.file.server.common.Constants.CACHE_TIMEOUT_AMOUNT;
import static iudx.file.server.common.Constants.CAT_ITEM_PATH;

public class CatalogueServiceImpl implements CatalogueService {

  private static final Logger LOGGER = LogManager.getLogger(CatalogueServiceImpl.class);

  private WebClient webClient;
  private String host;
  private int port;

  private String catBasePath;
  private String catItemPath;
  private final Cache<String, List<String>> applicableFilterCache =
      CacheBuilder.newBuilder().maximumSize(1000)
          .expireAfterAccess(CACHE_TIMEOUT_AMOUNT, TimeUnit.MINUTES).build();

  public CatalogueServiceImpl(WebClientFactory webClientFactory, JsonObject config) {
    this.webClient = webClientFactory.getWebClientFor(ServerType.FILE_SERVER);
    this.host = config.getString("catalogueHost");
    this.port = config.getInteger("cataloguePort");
    catBasePath = config.getString("dxCatalogueBasePath");
    catItemPath = catBasePath + CAT_ITEM_PATH;
  }

  @Override
  public Future<Boolean> isAllowedMetaDataField(MultiMap params) {
    Promise<Boolean> promise = Promise.promise();
    promise.complete(true);
    return promise.future();
  }

  @Override
  public Future<List<String>> getAllowedFilters4Queries(String id) {
    Promise<List<String>> promise = Promise.promise();
    // Note: id should be a complete id not a group id (ex : domain/SHA/rs/rs-group/itemId)
    String groupId = id.substring(0, id.lastIndexOf("/"));
    List<String> filters = applicableFilterCache.getIfPresent(id);
    if (filters == null) {
      // check for group if not present by item key.
      filters = applicableFilterCache.getIfPresent(groupId + "/*");
    }
    if (filters == null) {
      fetchFilters4Item(id, groupId).onComplete(handler -> {
        if (handler.succeeded()) {
          promise.complete(handler.result());
        } else {
          promise.fail("failed to fetch filters.");
        }
      });
    } else {
      promise.complete(filters);
    }
    return promise.future();
  }

  private Future<List<String>> fetchFilters4Item(String id, String groupId) {
    Promise<List<String>> promise = Promise.promise();
    Future<List<String>> getItemFilters = getFilterFromId(id);
    Future<List<String>> getGroupFilters = getFilterFromId(groupId);
    getItemFilters.onComplete(itemHandler -> {
      if (itemHandler.succeeded()) {
        List<String> filters4Item = itemHandler.result();
        if (filters4Item.isEmpty()) {
          getGroupFilters.onComplete(groupHandler -> {
            if (groupHandler.succeeded()) {
              List<String> filters4Group = groupHandler.result();
              applicableFilterCache.put(groupId + "/*", filters4Group);
              promise.complete(filters4Group);
            } else {
              LOGGER.error(
                  "Failed to fetch applicable filters for id: " + id + "or group id : " + groupId);
            }
          });
        } else {
          applicableFilterCache.put(id, filters4Item);
          promise.complete(filters4Item);
        }
      } else {
        promise.fail(itemHandler.cause());
      }
    });
    return promise.future();
  }


  private Future<List<String>> getFilterFromId(String id) {
    Promise<List<String>> promise = Promise.promise();
    callCatalogueAPI(id, handler -> {
      if (handler.succeeded()) {
        promise.complete(handler.result());
      } else {
        promise.fail("failed to fetch filters for id : " + id);
      }
    });
    return promise.future();
  }

  private void callCatalogueAPI(String id, Handler<AsyncResult<List<String>>> handler) {
    List<String> filters = new ArrayList<String>();
    webClient.get(port, host, catItemPath).addQueryParam("id", id).send(catHandler -> {
      if (catHandler.succeeded()) {
        JsonArray response = catHandler.result().bodyAsJsonObject().getJsonArray("results");
        response.forEach(json -> {
          JsonObject res = (JsonObject) json;
          if (res.containsKey("iudxResourceAPIs")) {
            filters.addAll(toList(res.getJsonArray("iudxResourceAPIs")));
          }
        });
        handler.handle(Future.succeededFuture(filters));
      } else if (catHandler.failed()) {
        LOGGER.error("catalogue call ("+ catItemPath + ") failed for id" + id);
        handler.handle(Future.failedFuture("catalogue call(" + catItemPath + ") failed for id" + id));
      }
    });
  }

  private <T> List<T> toList(JsonArray arr) {
    if (arr == null) {
      return null;
    } else {
      return (List<T>) arr.getList();
    }
  }

  @Override
  public Future<Boolean> isItemExist(String id) {
    LOGGER.debug("isItemExist() started");
    Promise<Boolean> promise = Promise.promise();
    LOGGER.info("id : " + id);
    webClient.get(port, host, catItemPath).addQueryParam("id", id)
        .expect(ResponsePredicate.JSON).send(responseHandler -> {
          if (responseHandler.succeeded()) {
            HttpResponse<Buffer> response = responseHandler.result();
            JsonObject responseBody = response.bodyAsJsonObject();
            if (responseBody.getString("type").equalsIgnoreCase("urn:dx:cat:Success")
                && responseBody.getInteger("totalHits") > 0) {
              promise.complete(true);
            } else {
              promise.fail(responseHandler.cause());
            }
          } else {
            promise.fail(responseHandler.cause());
          }
        });
    return promise.future();
  }

}
