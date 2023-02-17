package ch.niceideas.eskimo.model.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ServiceUser {

    private final String userName;
    private final int userId;

    @Override
    public String toString() {
        return userName+":"+userId;
    }
}
