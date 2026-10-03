package net.enderirt.smalllogictweaks.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation khai báo siêu dữ liệu cho trường cấu hình: chú thích, giới hạn biên và giá trị mặc định.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigEntry {
    /**
     * Chú thích giải thích chức năng của biến cấu hình.
     */
    String comment() default "";

    /**
     * Giới hạn giá trị số nguyên nhỏ nhất.
     */
    int minInt() default Integer.MIN_VALUE;

    /**
     * Giới hạn giá trị số nguyên lớn nhất.
     */
    int maxInt() default Integer.MAX_VALUE;

    /**
     * Giá trị mặc định an toàn khi thông số bị out of bounds.
     */
    int defaultInt() default 0;
}
