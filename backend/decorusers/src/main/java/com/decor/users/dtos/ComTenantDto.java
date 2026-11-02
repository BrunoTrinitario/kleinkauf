package com.decor.users.dtos;

public record ComTenantDto (
        String id,
        String name,
        String dbName,
        String dbUser,
        String dbPassword
){

}
