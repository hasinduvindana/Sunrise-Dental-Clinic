package service.notify;

import dao.DAOFactory;

/** Writes every published event into the audit_log table. */
public class AuditTrailListener implements ClinicEventListener {

    @Override
    public void onEvent(ClinicEvent event) {
        Object actorId = event.get("actorId");
        Integer userId = actorId instanceof Integer ? (Integer) actorId : null;
        DAOFactory.getInstance().audit().log(
                userId,
                String.valueOf(event.get("actorRole")),
                event.getType().name(),
                String.valueOf(event.get("entity")),
                String.valueOf(event.get("entityId")),
                event.getMessage());
    }
}
