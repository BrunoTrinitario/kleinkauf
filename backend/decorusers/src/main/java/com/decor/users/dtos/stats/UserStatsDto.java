package com.decor.users.dtos.stats;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class UserStatsDto {
    private int usersTotal;
    private int usersActive;
    private HashMap<String, Integer> periodRegistration;
}
