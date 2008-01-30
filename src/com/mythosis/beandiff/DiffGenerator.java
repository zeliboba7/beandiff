package com.mythosis.beandiff;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author tonior@gmail.com
 */
public class DiffGenerator {
    private static final Logger logger = Logger.getLogger(DiffGenerator.class.getSimpleName());

    private final Map<String, DataResolver> resolvers = new HashMap<String, DataResolver>();

    /**
     * Calculates the difference between two objects.
     * <p/>
     * This method uses the {@link Diffable} and {@link DiffField} annotations to
     * hierarchically contruct a tree-like map with the differences between two objects.
     * Specifically, the map contains an entry for each difference between the two objects,
     * where the <code>key</code> indicates where the difference ocurrs, and the <code>value</code>
     * indicates the original value.  The key names are constructed using a starting <code>tag</code>
     * with the field names appended.
     * <p/>
     * The <code>Diffable</code> annotation is used to tell <code>DiffGenerator</code>'s <code>diff()</code>
     * method that that class is prepared for it.  The <code>DiffField</code> annotation tells the
     * <code>diff()</code> method that that field should be included when calculating the difference.
     *
     * @param tag      initial key name for difference map
     * @param original original object
     * @param current  new object
     * @return a <code>Map&lt;String, String&gt;</code> with the differences between the original and new objects,
     *         where the <code>key</code>s are the fields where the differences occur, and the <code>value</code>s
     *         are the original values.
     * @throws IllegalArgumentException If the two objects to compare are not of the same class.
     * @see Diffable
     * @see DiffField
     */
    public Map<String, String> diff(String tag, Object original, Object current) {
        if (tag == null) tag = "";
        final String prefix = tag.equals("") ? "" : (tag + ".");
        Map<String, String> returnValue = new TreeMap<String, String>();

        if (original != null && current != null && original.getClass() != current.getClass())
            throw new RuntimeException("'original' and 'current' arguments not same,  This usually happens with" +
                    " persistent collections (since they are accessed with a proxy object.). system will try to diff anyway." +
                    " Original:" + original.getClass().getName() + " Current:" + current.getClass().getName());

        // Special case when either or both values are null is handled below
        if (original != null && current != null) {
            final Class<?> objectClass = original.getClass();
            logger.finer("Diffing objects of type: " + objectClass.getSimpleName());
            // Check whether the class is Diffable.  Diffable classes are handled specially.
            if (objectClass.isAnnotationPresent(Diffable.class)) {
                logger.finer(objectClass.getSimpleName() + " is Diffable");
                for (Field field : ObjectUtils.getAllFields(objectClass)) {
                    // Only check fields annotated with DiffField.
                    if (field.isAnnotationPresent(DiffField.class)) {
                        Object originalFieldValue;
                        Object currentFieldValue;
                        try {
                            originalFieldValue = ObjectUtils.getValueForField(field, original);
                        } catch (IllegalAccessException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        } catch (InvocationTargetException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        }
                        try {
                            currentFieldValue = ObjectUtils.getValueForField(field, current);
                        } catch (IllegalAccessException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        } catch (InvocationTargetException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        }
                        // Resolve the data, in case some sort of lookup or any other processing is needed.
                        DiffField annotation = field.getAnnotation(DiffField.class);
                        String dataType = annotation.value();
                        DataResolver resolver;
                        synchronized (this) {
                            resolver = resolvers.get(dataType);
                        }
                        if (resolver != null) {
                            logger.finer("Resolving data...");
                            originalFieldValue = resolver.resolve(originalFieldValue);
                            currentFieldValue = resolver.resolve(currentFieldValue);
                            logger.finer("Both data resolved.");
                        }
                        // Recursively call diff() on the two values, appending the field name to the tag.
                        returnValue.putAll(this.diff(prefix + field.getName(), originalFieldValue, currentFieldValue));
                    }
                }
            } else {
                // For non-Diffable classes...

                logger.finer(objectClass.getSimpleName() + " is not Diffable.");
                // Iterate through iterable objects
                if (original instanceof Iterable) {
                    logger.finer(objectClass.getSimpleName() + " is Iterable.");
                    int i = 0;
                    Iterator<?> oIterator = ((Iterable<?>) original).iterator();
                    Iterator<?> cIterator = ((Iterable<?>) current).iterator();
                    while (oIterator.hasNext() && cIterator.hasNext()) {
                        logger.finer("Checking item with index: " + i);
                        Object oObj = oIterator.next();
                        Object cObj = cIterator.next();
                        // Recursively call diff() on the corresponding values, appending the index.
                        returnValue.putAll(this.diff(prefix + "idx" + ++i, oObj, cObj));
                    }

                    // If the item count is different, record it.
                    if (oIterator.hasNext()) {
                        while (oIterator.hasNext()) {
                            oIterator.next();
                            i++;
                        }
                        returnValue.put(prefix + "count", Integer.toString(i));
                    } else if (cIterator.hasNext()) {
                        returnValue.put(prefix + "count", Integer.toString(i));
                    }
                    // Iterate through map keys
                } else if (original instanceof Map) {
                    Map<?, ?> oMap = (Map<?, ?>) original;
                    Map<?, ?> cMap = (Map<?, ?>) current;
                    for (Object key : oMap.keySet()) {
                        Object oObj = oMap.get(key);
                        Object cObj = cMap.get(key);
                        // Recursively call diff() on the corresponding vaues, appending the key.
                        returnValue.putAll(this.diff(prefix + key.toString(), oObj, cObj));
                    }
                    // If class isn't Diffable, not iterable, and not a map, simply use equals() to find any differences
                } else if (!original.equals(current)) {
                    returnValue.put(tag, original.toString());
                }
            }
            // Special case when either, but not both, is null.  If both are null, there is no difference to record.
        } else if (original != current) {
            if (original == null)
                returnValue.put(tag, "");
            else
                returnValue.putAll(resolveObject(tag, original));
//            returnValue.put(tag, original == null ? "" : original.toString());
        }

        return returnValue;
    }

    /**
     * Resolves an object using {@link Diffable Diffable} fields as appropriate.
     * <p/>
     * This method is used internally by the <code>diff()</code> method to add the correct values
     * when the <code>current</code> object is <code>null</code> at any given point in the comparation.
     *
     * @param tag    initial key name for map
     * @param object object to be resolved
     * @return a map with all the data in the object, according to normal {@link DiffGenerator DiffGenerator} rules
     */
    public Map<String, String> resolveObject(String tag, Object object) {
        if (tag == null) tag = "";
        final String prefix = tag.equals("") ? "" : (tag + ".");
        Map<String, String> returnValue = new TreeMap<String, String>();

        if (object == null)
            returnValue.put(tag, "");
        else {
            final Class<?> objectClass = object.getClass();
            logger.finer("Resolving object of type: " + objectClass.getSimpleName());
            // Check whether the class is Diffable.  Diffable classes are handled specially.
            if (objectClass.isAnnotationPresent(Diffable.class)) {
                logger.finer(objectClass.getSimpleName() + " is Diffable");
                for (Field field : ObjectUtils.getAllFields(objectClass)) {
                    // Only check fields annotated with DiffField.
                    if (field.isAnnotationPresent(DiffField.class)) {
                        Object fieldValue;
                        try {
                            fieldValue = ObjectUtils.getValueForField(field, object);
                        } catch (IllegalAccessException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        } catch (InvocationTargetException e) {
                            logger.severe("Error accessing field \"" + field.getName() + "\" in diff. Skipping." + e);
                            continue;
                        }
                        // Resolve the data, in case some sort of lookup or any other processing is needed.
                        DiffField annotation = field.getAnnotation(DiffField.class);
                        String dataType = annotation.value();
                        DataResolver resolver;
                        synchronized (this) {
                            resolver = resolvers.get(dataType);
                        }
                        if (resolver != null) {
                            logger.finer("Resolving data...");
                            fieldValue = resolver.resolve(fieldValue);
                            logger.finer("Data resolved.");
                        }
                        // Recursively call resolveObject() on the two values, appending the field name to the tag.
                        returnValue.putAll(this.resolveObject(prefix + field.getName(), fieldValue));
                    }
                }
            } else {
                // For non-Diffable classes...

                logger.finer(objectClass.getSimpleName() + " is not Diffable.");
                // Iterate through iterable objects
                if (object instanceof Iterable) {
                    logger.finer(objectClass.getSimpleName() + " is Iterable.");
                    int i = 0;
                    for (Object o : ((Iterable<?>) object)) {
                        logger.finer("Checking item with index: " + i);
                        // Recursively call resolveObject() on the corresponding values, appending the index.
                        returnValue.putAll(this.resolveObject(prefix + "idx" + ++i, o));
                    }
                    // Iterate through map keys
                } else if (object instanceof Map) {
                    Map<?, ?> oMap = (Map<?, ?>) object;
                    for (Object key : oMap.keySet()) {
                        Object obj = oMap.get(key);
                        // Recursively call resolveObject() on the corresponding vaues, appending the key.
                        returnValue.putAll(this.resolveObject(prefix + key.toString(), obj));
                    }
                    // If class isn't Diffable, not iterable, and not a map, simply add the object as a string
                } else {
                    returnValue.put(tag, object.toString());
                }
            }

        }

        return returnValue;
    }

    /**
     * Registers a {@link DataResolver DataResolver} to resolve data of type <code>forType<code>.
     * <p/>
     * The <code>diff()</code> method can resolve data, using a <code>DataResolver</code>.  The field's
     * {@link DiffField DiffField} annotation can define a data type for the field, which the
     * <code>diff()</code> method will then lookup in its registered resolvers, and pass the value
     * found in the actual field to this resolver, and use the result for the actual difference calculation.
     *
     * @param forType  the user-defined and application-specific field/data type to register a resolver for
     * @param resolver the resolver to register for the field/data type
     * @return the <code>DataResolver</code> previously registered for this data type, if any, or <code>null</code> otherwise
     * @see DataResolver
     * @see DiffGenerator#unregisterDataResolver(String)
     * @see DiffField
     */
    public synchronized DataResolver registerDataResolver(String forType, DataResolver resolver) {
        DataResolver old = resolvers.get(forType);
        resolvers.put(forType, resolver);
        return old;
    }

    /**
     * Unregisters a {@link DataResolver DataResolver}.
     *
     * @param forType the field/data type for which to unregister the resolver
     * @see DataResolver
     * @see DiffGenerator#registerDataResolver(String, DataResolver)
     * @see DiffField
     */
    public synchronized void unregisterDataResolver(String forType) {
        resolvers.remove(forType);
    }

    public static void main(String[] args) throws NoSuchFieldException {

        class Tmp {
            public List<?> a;
        }

        Class<Tmp> c = Tmp.class;

        Field f = c.getField("a");
        Type t = f.getGenericType();
        Class<?> cls = f.getType();
        System.out.println(t instanceof Class);
        System.out.println(t == cls);
        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            for (Type type : pt.getActualTypeArguments()) {
                System.out.println("Parameter: " + type);
            }
        } else if (t instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) t;
            System.out.println("Component: " + gat.getGenericComponentType());
        } else {
            System.out.println("non generic");
        }
/*
        for(TypeVariable<Class<List<String>>> typeVariable : f.getGenericType().)
        {
            for(Type type : typeVariable.getBounds())
            {
                System.out.println(type);
            }
        }
*/
//        f.getType().getTypeParameters()

        @Diffable
        class ClassB {
            public long id;
            @DiffField("string")
            public String name;
            @DiffField
            public int num;
        }

        @Diffable
        class ClassAA {
            @DiffField
            public int foo = 1;
        }

        @Diffable
        class ClassA extends ClassAA {
            @DiffField("profile_id")
            private int a;
            @DiffField
            public String b;
            @DiffField
            public List<String> messageList;
            @DiffField
            public Map<String, ClassB> nameMap;

            public int getA() {
                return a;
            }

            @DiffField
            private boolean bool = true;

            public boolean isBool() {
                return bool;
            }
        }

        DiffGenerator dg = new DiffGenerator();

        dg.registerDataResolver("profile_id", new DataResolver<Integer, String>() {
            public String resolve(Integer param) {
                int id = (Integer) param;
                switch (id) {
                    case 1:
                        return "This is ID 1";
                    case 2:
                        return "This is ID 2";
                    default:
                        return "unknown";
                }
            }

            public Class<Integer> getDataType() {
                return Integer.class;
            }
        });
        logger.info("Test");

        ClassA obj1 = new ClassA();
        obj1.a = 1;
        obj1.b = "Hello world";
        obj1.messageList = new ArrayList<String>();
        obj1.messageList.add("String 1");
        obj1.messageList.add("String 2");
        obj1.nameMap = new HashMap<String, ClassB>();
        ClassB tmp1 = new ClassB();
        tmp1.id = 1;
        tmp1.name = "Tonio";
        tmp1.num = 10;
        obj1.nameMap.put("tonio", tmp1);
        tmp1 = new ClassB();
        tmp1.id = 2;
        tmp1.name = "Douglas";
        tmp1.num = 20;
        obj1.nameMap.put("douglas", tmp1);

        ClassA obj2 = new ClassA();
        obj2.foo = 10;
        obj2.a = 2;
        obj2.b = "G'bye world";
        obj2.messageList = new ArrayList<String>();
        obj2.messageList.add("String 1");
        obj2.messageList.add("String two");
        obj2.messageList.add("String 3");
        obj2.nameMap = new HashMap<String, ClassB>();
        obj2.bool = false;
        tmp1 = new ClassB();
        tmp1.id = 3;
        tmp1.name = "Toner";
        tmp1.num = 10;
        obj2.nameMap.put("tonio", tmp1);
        tmp1 = new ClassB();
        tmp1.id = 4;
        tmp1.name = "Douglas";
        tmp1.num = 20;
        obj2.nameMap.put("douglas", tmp1);

        Map<String, String> diffs = dg.diff("objects", obj1, obj2);
        for (String key : diffs.keySet()) {
            System.out.println(key + " = " + diffs.get(key));
        }
    }
}
