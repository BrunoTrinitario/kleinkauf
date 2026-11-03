package com.kleinkauf.users.dtos;

public record ComTenantDto (
        String id,
        String name,
        String dbName,
        String dbUser,
        String dbPassword
){

}
