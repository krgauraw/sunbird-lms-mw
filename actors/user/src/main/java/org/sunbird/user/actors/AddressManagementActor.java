package org.sunbird.user.actors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.datasecurity.EncryptionService;
import org.sunbird.common.request.Request;
import org.sunbird.user.dao.AddressDao;
import org.sunbird.user.dao.impl.AddressDaoImpl;
import org.sunbird.user.util.UserActorOperations;

@ActorConfig(
  tasks = {"upsertUserAddress"},
  asyncTasks = {"upsertUserAddress"}
)
public class AddressManagementActor extends BaseActor {

  private EncryptionService encryptionService =
      org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.getEncryptionServiceInstance(
          null);
  private AddressDao addressDao = AddressDaoImpl.getInstance();

  @Override
  public void onReceive(Request request) throws Throwable {
    if (UserActorOperations.UPSERT_USER_ADDRESS
        .getValue()
        .equalsIgnoreCase(request.getOperation())) {
      upsertAddress(request);
    } else {
      onReceiveUnsupportedOperation("AddressManagementActor");
    }
  }

  @SuppressWarnings("unchecked")
  private void upsertAddress(Request request) {
    Map<String, Object> requestMap = request.getRequest();
    String operationtype = (String) requestMap.get(JsonKey.OPERATION_TYPE);
    List<Map<String, Object>> addressList =
        (List<Map<String, Object>>) requestMap.get(JsonKey.ADDRESS);
    Response response = new Response();
    List<String> errMsgs = new ArrayList<>();
    try {
      String encUserId = encryptionService.encryptData((String) requestMap.get(JsonKey.ID));
      String encCreatedById =
          encryptionService.encryptData((String) requestMap.get(JsonKey.CREATED_BY));
      for (int i = 0; i < addressList.size(); i++) {
        Map<String, Object> address = addressList.get(i);
        if (JsonKey.CREATE.equalsIgnoreCase(operationtype)) {
          address.put(JsonKey.CREATED_BY, encCreatedById);
          address.put(JsonKey.USER_ID, encUserId);
          address.put(JsonKey.ID, ProjectUtil.getUniqueIdFromTimestamp(1));
          address.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
          addressDao.createAddress(address);
        } else {
          // update
          if (address.containsKey(JsonKey.IS_DELETED)
              && null != address.get(JsonKey.IS_DELETED)
              && ((boolean) address.get(JsonKey.IS_DELETED))
              && !StringUtils.isBlank((String) address.get(JsonKey.ID))) {
            addressDao.deleteAddress((String) address.get(JsonKey.ID));
            continue;
          }

          if (!address.containsKey(JsonKey.ID)) {
            address.put(JsonKey.CREATED_BY, encCreatedById);
            address.put(JsonKey.USER_ID, encUserId);
            address.put(JsonKey.ID, ProjectUtil.getUniqueIdFromTimestamp(1));
            address.put(JsonKey.CREATED_DATE, ProjectUtil.getFormattedDate());
            addressDao.createAddress(address);
          } else {
            address.put(JsonKey.UPDATED_BY, encCreatedById);
            address.put(JsonKey.UPDATED_DATE, ProjectUtil.getFormattedDate());
            address.remove(JsonKey.USER_ID);
            addressDao.updateAddress(address);
          }
        }
      }
    } catch (Exception e) {
      errMsgs.add(e.getMessage());
      ProjectLogger.log(e.getMessage(), e);
    }
    if (CollectionUtils.isNotEmpty(errMsgs)) {
      response.put(JsonKey.ADDRESS + ":" + JsonKey.ERROR_MSG, errMsgs);
    } else {
      response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    }
    sender().tell(response, self());
  }
}
