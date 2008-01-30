package com.mythosis.beandiff;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author tonior@gmail.com
 */
class ObjectUtils {
    private static final Logger logger = Logger.getLogger(ObjectUtils.class.getSimpleName());

    /**
     * Reflectively attempts to access a field.
     * <p/>
     * The method first tries to use a getter method using traditional beans naming conventions.  If it
     * fails, and the field was a <code>boolean</code> or <code>Boolean</code>, it tries a
     * <code>get</code>-prefixed getter (as opposed to the conventional <code>is</code>-prefixed getter
     * for <code>booleans</code> and <code>Booleans</code>.  Finally, it tries to access the field
     * directly.
     *
     * @param field  the field to access
     * @param object the object on which to access the field
     * @return the value of the field on that object
     * @throws IllegalAccessException if no getter was found, and the field was not accessible, or if a getter method was found, but was not accessible
     * @throws java.lang.reflect.InvocationTargetException
     *                                if the getter method throws an exception
     */
    public static Object getValueForField(Field field, Object object) throws IllegalAccessException, InvocationTargetException {

        String boolPrefix = "is";
        String normalPrefix = "get";
        String methodTail = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
        if (field.getType() == Boolean.class || field.getType() == boolean.class)
            try {
                String methodName = boolPrefix + methodTail;
                Method getter = object.getClass().getMethod(methodName);
                return getter.invoke(object);
            } catch (NoSuchMethodException noSuchMethodException) {
                logger.fine("Didn't find proper boolean getter, trying normal getter.");
            }
        String methodName = normalPrefix + methodTail;
        try {
            Method getter = object.getClass().getMethod(methodName);
            return getter.invoke(object);
        } catch (NoSuchMethodException e) {
            return field.get(object);
        }
    }

    /**
     * Returns a list of all the fields of a class.
     * <p/>
     * This method not only includes fields of the class itself, but also of superclasses.
     *
     * @param objectClass the class for which you want the list of fields
     * @return the list of fields
     * @see Class#getDeclaredFields()
     * @see Class#getSuperclass()
     */
    public static List<Field> getAllFields(Class objectClass) {
        List<Field> returnValue = new ArrayList<Field>();
        for (Class c = objectClass; c != null; c = c.getSuperclass()) {
            returnValue.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        return returnValue;
    }
}

