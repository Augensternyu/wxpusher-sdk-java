package com.smjcco.wxpusher.client.sdk.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smjcco.wxpusher.client.sdk.demo.result.*;
import com.smjcco.wxpusher.demo.result.*;

import com.smjcco.wxpusher.client.sdk.demo.utils.ThrowableUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用程序配置，包括配置拦截器，异常等；
 */
@Configuration
@Slf4j
public class AppWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //打印请求日志
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                log.info(getIpAddress(request) + "-" + request.getMethod() + "[" + request.getRequestURI() + "]");
                return true;
            }
        });
    }

    @Override
    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add((request, response, handler, ex) -> {
            Throwable throwable = ThrowableUtils.getRootThrowable(ex);
            Result result;
            if (throwable instanceof BizException) {
                //业务异常,不需要打印堆栈
                result = new Result(((BizException) throwable).getResultCode(), throwable.getMessage());
                responseResult(response, result);
                log.warn("{}-{}", request.getRequestURI(), throwable.getMessage());
            } else if (throwable instanceof AppException) {
                //应用异常
                result = new Result(((AppException) throwable).getResultCode(), "服务器出现应用异常:" + throwable.getMessage());
                responseResult(response, result);
                log.error("{}-{}", request, throwable.getMessage());
            } else if (throwable instanceof NoHandlerFoundException || throwable instanceof HttpRequestMethodNotSupportedException) {
                //不是服务器的异常,不需要打印堆栈
                result = new Result(ResultCode.NOT_FOUND, "接口[(" + request.getMethod() + ")" + request.getRequestURI() + "]不存在");
                responseResult(response, result);
                log.warn("{}-{}", result, throwable.getMessage());
            } else if (throwable instanceof BindException) {
                //参数不合法
                List<ObjectError> errors = ((BindException) throwable).getAllErrors();
                if (!errors.isEmpty()) {
                    result = new Result(ResultCode.BIZ_FAIL, errors.get(0).getDefaultMessage());
                } else {
                    result = new Result(ResultCode.BIZ_FAIL, "数据验证错误");
                }
                responseResult(response, result);
                log.warn("参数错误", throwable);
            } else if (throwable instanceof ServletException) {
                result = new Result(ResultCode.INTERNAL_SERVER_ERROR, "服务器错误:" + throwable.getMessage());
                responseResult(response, result);
                log.error(result.toString(), throwable);
            }else if (throwable instanceof HttpException){
                //返回原始的http status code
                result = new Result(ResultCode.INTERNAL_SERVER_ERROR, "服务器错误:" + throwable.getMessage());
                responseResult(response, result,((HttpException) throwable).getHttpStatus());
            }  else {
                //其他错误
                String message = String.format("接口 [%s] 出现异常", request.getRequestURI());
                result = new Result(ResultCode.INTERNAL_SERVER_ERROR, message);
                responseResult(response, result);
                log.error(result.toString(), throwable);
            }
            return new ModelAndView();
        });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        //配置跨域请求
        registry.addMapping("/**")
                .allowedOrigins("https://wxpusher.zjiecode.com")
                .allowedOrigins("*")
                .allowedHeaders("*")
                .allowCredentials(false)
                .allowedMethods("*");
    }

    private void responseResult(HttpServletResponse response, Result result) {
        responseResult(response, result,200);
    }
        /**
         * 遇到错误，拦截以后输出响应到客户端
         */
    private void responseResult(HttpServletResponse response, Result result,int httpCode) {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-type", "application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Allow-Methods", "*");
        response.setStatus(httpCode);
        try {
            ObjectMapper mapper = new ObjectMapper();
            response.getWriter().write(mapper.writeValueAsString(result));
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
    }

    /**
     * 获取客户端ip
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，那么取第一个ip为客户端ip
        if (ip != null && ip.indexOf(",") != -1) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        return ip;
    }
}
