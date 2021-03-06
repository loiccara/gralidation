package org.yamevetwo.gralidation

import org.apache.commons.lang3.ClassUtils

class Gralidator {

    static final String ERROR_CODE_PREFIX = "gralidation.error."

    /**
     * Add the gralidate(Object object) to the Object class
     *
     * @return
     */
    static def initGralidator() {
        Object.metaClass.gralidate = {
            return gralidate(delegate)
        }
    }

    /**
     * Validate an object wiith a map of static constraints existing in the object's Class.
     * Example:
     * class DummyClass {
     *     String name
     *     static constraints = [name:[nullable:true]]
     * }
     * @param object
     * @return
     */
    static GralidationResult gralidate(Object object) {
        Map objectConstraints = object.constraints
        gralidate(object, objectConstraints)
    }

    /**
     * Validate an object with a given map of constraints
     *
     * @param object
     * @param constraints (example : [name:[nullable:true]])
     * @return
     */
    static GralidationResult gralidate(Object object, Map constraints) {
        List<String> errors = []
        if (constraints == null) {
            throw new MissingPropertyException("No constraints specified for ${object.class}")
        }

        constraints.each { String propertyName, Map controls ->
            def propertyValue = object.getProperties().get(propertyName)
            GralidationResult tempResult = executeControls(propertyName, propertyValue, controls)
            errors.addAll(tempResult.errors)

            if (propertyValue && isEmbeddedComplexObject(object, propertyName, propertyValue)) {
                // gralidate the embedded object
                GralidationResult embeddedResult = gralidate(propertyValue)
                errors.addAll(embeddedResult.errors)
            }
        }
        new GralidationResult(isValid: errors.isEmpty(), errors: errors)
    }

    /**
     * Validate a map of <key,value> with a given map of constraints
     * 
     * @param myMap
     * @param constraints
     * @param checkInexistantKeys
     * @return
     */
    static GralidationResult gralidate(Map myMap, Map constraints, boolean checkInexistantKeys) {
        List<String> errors = []
        constraints.each { String propertyName, Map controls ->
            if (myMap?.containsKey(propertyName)) {
                def propertyValue = myMap.get(propertyName)
                GralidationResult tempResult = executeControls(propertyName, propertyValue, controls)
                errors.addAll(tempResult.errors)
            } else if (checkInexistantKeys) {
                errors.add(ERROR_CODE_PREFIX + "inexistantProperty:" + propertyName)
            }
        }
        new GralidationResult(isValid: errors.isEmpty(), errors: errors)
    }

    protected static GralidationResult controlList(String propertyName, List myList, Map controls) {
        List errors = []
        myList.each {
            GralidationResult thisGralidation = executeControls(propertyName, it, controls)
            errors.addAll(thisGralidation.errors)
        }
        new GralidationResult(isValid: errors.isEmpty(), errors: errors)
    }

    private static GralidationResult executeControls(String propertyName, def propertyValue, Map controls) {
        List errors = []
        controls.each { String constraint, def controlValue ->
            GralidationEnum currentControl = constraint.toUpperCase() as GralidationEnum
            def thisResult = currentControl.control.call(propertyName, propertyValue, controlValue)
            if (!thisResult.isValid) {
                if (currentControl.isMultipleControl) {
                    errors.add(thisResult.errors)
                } else {
                    errors.add(thisResult.errorData)
                }
            }
        }
        new GralidationResult(isValid: errors.isEmpty(), errors: errors)
    }

    private static boolean isEmbeddedComplexObject(Object object, String propertyName, def propertyValue) {

        Class propertyClass = object.properties.get(propertyName).class
        propertyClass && !ClassUtils.isPrimitiveOrWrapper(propertyClass) && !isArrayStringOrCollection(propertyValue) && !propertyClass.isEnum()
    }

    private static final boolean isArrayStringOrCollection(Object object) {
        boolean isArrayOrCOllection = [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
        boolean isCharSequence = object instanceof CharSequence
        isArrayOrCOllection || isCharSequence
    }
}
