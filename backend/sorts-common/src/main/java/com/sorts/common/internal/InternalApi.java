package com.sorts.common.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「仅供服务间调用」的接口。
 *
 * <p>被标记的 Controller（类或方法）会被 {@link InternalApiInterceptor} 拦截，
 * 要求请求头携带合法的 {@code X-Internal-Token}。</p>
 *
 * <p>为什么不用「路径前缀」判断：路径前缀靠约定，改名或新增路径就会静默失去保护；
 * 注解长在代码上，编译期可见、评审时看得见，漏保护的可能性低得多。</p>
 *
 * @author sorts
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalApi {

    /** 备注用途，仅作文档说明 */
    String value() default "";
}
