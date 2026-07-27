package csh.back.global.dto;

public record ResponseData<T> (
        int statusCode,
        T data
) {

}
