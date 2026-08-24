package com.project.backend.application.port.out;

import com.project.backend.application.dto.InvitationEmailMessage;

/** Delivers a previously persisted invitation request to its recipient. */
public interface InvitationEmailDeliveryPort {

    void deliver(InvitationEmailMessage invitation);
}
