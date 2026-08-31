package com.project.backend.application.port.out;

import com.project.backend.application.dto.OutboundEmailMessage;

/** Delivers a previously persisted transactional message to its recipient. */
public interface OutboundEmailDeliveryPort {

    void deliver(OutboundEmailMessage message);
}
