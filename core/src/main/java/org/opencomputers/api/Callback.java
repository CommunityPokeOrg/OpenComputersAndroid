package org.opencomputers.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public method on a {@link Component} as callable from Lua via
 * {@code component.invoke(address, name, ...)} — the OpenComputers callback model.
 *
 * Methods must accept exactly one {@code Object[]} parameter and may return
 * {@code void}, {@code Object}, or {@code Object[]}. Arguments arrive as Lua
 * types already converted by the machine layer (String, Double, Boolean,
 * byte[], or Map&lt;Integer,Object&gt; for tables).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Callback {
    /** Human-readable signature shown by {@code component.methods()}. */
    String doc() default "";
}
