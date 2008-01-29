package com.mythosis.beandiff;

import java.lang.annotation.*;

/**
 * Indicates that this class is {@link com.mythosis.beandiff.DiffGenerator#diff(String, Object, Object) DiffGenerator.diff()}-aware.
 * <p/>
 * A class that uses this annotation should use {@link DiffField} to specify
 * which fields are to be included when calculating the difference between objects
 * of this type.
 *
 * @see DiffField
 * @see com.mythosis.beandiff.DiffGenerator#diff(String, Object, Object)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Diffable {
}
