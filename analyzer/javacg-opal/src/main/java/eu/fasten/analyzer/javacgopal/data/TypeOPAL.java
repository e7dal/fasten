package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class TypeOPAL{

    private static Logger logger = LoggerFactory.getLogger(TypeOPAL.class);

    private List<Method> methods;
    private Chain<ObjectType> superClasses;
    private List<ObjectType> superInterfaces;

    public List<Method> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public void setMethods(List<Method> methods) {
        this.methods = methods;
    }

    public void setSuperClasses(Chain<ObjectType> superClasses) {
        this.superClasses = superClasses;
    }

    public void setSuperInterfaces(List<ObjectType> superInterfaces) {
        this.superInterfaces = superInterfaces;
    }

    public TypeOPAL() {
        this.methods = new ArrayList<>();
        this.superClasses = null;
        this.superInterfaces = new ArrayList<>();
    }

    /**
     * Sets super classes and super interfaces of this type
     *
     * @param classHierarchy org.opalj.br.ClassHierarchy
     * @param currentClass org.opalj.br.ObjectType. The type that its supper types should be set.
     */
    public void setSupers(ClassHierarchy classHierarchy, ObjectType currentClass) {

        if (classHierarchy.supertypes().contains(currentClass)) {

            try {
                this.superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
            } catch (NoSuchElementException e) {
                logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder method.", currentClass, e);
            } catch (OutOfMemoryError e) {
                logger.error("This type {} made an out of memory Exception in calculation of its supper types!", currentClass, e);
            } catch (Exception e) {
                logger.error("This type made an Exception in calculation of its supper types!", e);
            }

            if (superClasses != null) {
                superClasses.reverse();
            }

            classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
                JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
            );
        }else {
            logger.warn("Opal class hierarchy didn't include super types of {}", currentClass);
        }
    }
}
