package org.vaadin.addon.cdiproperties;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.google.gwt.thirdparty.guava.common.collect.Lists;
import com.google.gwt.thirdparty.guava.common.collect.Sets;
import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.vaadin.data.Property;
import com.vaadin.data.util.BeanItem;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.AbstractOrderedLayout;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.LegacyWindow;
import com.vaadin.ui.LoginForm;
import com.vaadin.ui.TextField;
import com.vaadin.ui.components.colorpicker.ColorPickerGrid;
import com.vaadin.ui.components.colorpicker.ColorPickerHistory;
import com.vaadin.ui.components.colorpicker.ColorPickerSelect;
import org.vaadin.addon.cdiproperties.Generator.ComponentModel.ComponentProperty;

class Generator {

    private static Set<Class<? extends AbstractComponent>> excludedClasses = Sets
            .newHashSet(ColorPickerGrid.class, ColorPickerHistory.class,
                    ColorPickerSelect.class, LegacyWindow.class,
                    LoginForm.class);
    private static Set<String> excludedProperties = Sets.newHashSet("UI",
            "componentError", "connectorEnabled", "connectorId", "width",
            "height", "stateType", "type", "styleName", "timeFormat");
    private static Set primitiveWrapperClasses = Sets.newHashSet(Boolean.class,
            Byte.class, Character.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class);

    public static void main(String[] args) {

        Set<ComponentModel> componentModels = Sets.newHashSet();

        for (PojoClass pojoClass : PojoClassFactory
                .enumerateClassesByExtendingType("com.vaadin.ui",
                        Component.class, null)) {
            if ((pojoClass.isConcrete())
                    && !excludedClasses.contains(pojoClass.getClazz())) {
                Object implementation = getPojoInstance(pojoClass);

                if (implementation != null) {
                    ComponentModel componentModel = new ComponentModel(
                            pojoClass.getClazz());

                    Method[] pojoMethods = pojoClass.getClazz().getMethods();

                    // Add bean properties
                    BeanItem bi = new BeanItem(implementation);
                    for (Object pid : bi.getItemPropertyIds()) {

                        boolean setterFound = false;
                        for (Method pojoMethod : pojoMethods) {
                            if (pojoMethod.getName().equalsIgnoreCase(
                                    "set" + pid)) {
                                setterFound = true;
                                break;
                            }
                        }

                        if (setterFound && !excludedProperties.contains(pid)) {
                            Property property = bi.getItemProperty(pid);

                            Class type = property.getType();

                            if (primitiveWrapperClasses.contains(type)
                                    || type.isEnum() || type == String.class
                                    || type == Class.class) {

                                String defaultValue = formatDefaultValue(property
                                        .getValue());
                                if (type == String.class) {
                                    defaultValue = "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING";
                                }

                                ComponentProperty cp = new ComponentProperty(
                                        formatType(type), String.valueOf(pid),
                                        defaultValue);
                                componentModel.getProperties().add(cp);
                            }
                        }
                    }

                    // Add custom properties
                    componentModel.getProperties().addAll(
                            getCustomProperties(pojoClass, implementation));

                    try {
                        writeFile(
                                args[0]
                                        + "/"
                                        + componentModel.formatAnnotationClassName()
                                        + ".java",
                                componentModel.toAnnotation());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    componentModels.add(componentModel);
                }
            }
        }

        try {
            writeFile(args[1] + "/ComponentProducers.java",
                    toProducer(componentModels));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static Collection<? extends ComponentProperty> getCustomProperties(
            PojoClass pojoClass, Object implementation) {
        Collection<ComponentProperty> result = Sets.newHashSet();

        Component component = (Component) implementation;

        result.add(new ComponentProperty("float", "widthValue",
                formatDefaultValue(component.getWidth())));
        result.add(new ComponentProperty("float", "heightValue",
                formatDefaultValue(component.getHeight())));

        result.add(new ComponentProperty("com.vaadin.server.Sizeable.Unit",
                "widthUnits", "com.vaadin.server.Sizeable.Unit."
                        + component.getWidthUnits().name()));
        result.add(new ComponentProperty("com.vaadin.server.Sizeable.Unit",
                "heightUnits", "com.vaadin.server.Sizeable.Unit."
                        + component.getHeightUnits().name()));

        result.add(new ComponentProperty("String", "width",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));
        result.add(new ComponentProperty("String", "height",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));

        result.add(new ComponentProperty("Class", "implementation",
                formatDefaultValue(implementation.getClass())));

        result.add(new ComponentProperty("String", "captionKey",
                "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));

        result.add(new ComponentProperty("boolean", "sizeFull", "false"));
        result.add(new ComponentProperty("boolean", "sizeUndefined", "false"));

        result.add(new ComponentProperty("boolean", "localized", "true"));

        result.add(new ComponentProperty("String[]", "styleName", "{}"));

        if (implementation instanceof AbstractOrderedLayout
                || implementation instanceof GridLayout) {
            result.add(new ComponentProperty("boolean[]", "margin", "{}"));
        }

        if (implementation instanceof Label) {
            result.add(new ComponentProperty("String", "valueKey",
                    "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));
        }

        if (implementation instanceof AbstractComponent) {
            result.add(new ComponentProperty("String", "descriptionKey",
                    "org.vaadin.addon.cdiproperties.ComponentConfigurator.IGNORED_STRING"));
        }

        return result;
    }

    static String formatType(Class type) {
        String result = type.getCanonicalName();
        if (type == Boolean.class) {
            result = "boolean";
        } else if (type == Integer.class) {
            result = "int";
        } else if (type == Float.class) {
            result = "float";
        } else if (type == Double.class) {
            result = "double";
        }

        return result;
    }

    static String formatDefaultValue(Object defaultValue) {
        String result = String.valueOf(defaultValue);
        if (defaultValue instanceof String) {
            result = "\"" + defaultValue + "\"";
        } else if (defaultValue instanceof Float) {
            result = result.concat("f");
        } else if (defaultValue != null && defaultValue.getClass().isEnum()) {
            Enum e = (Enum) defaultValue;
            result = e.getClass().getCanonicalName() + "." + e.name();
        } else if (defaultValue instanceof Class) {
            result = ((Class) defaultValue).getCanonicalName() + ".class";
        }
        return result;
    }

    private static String toProducer(Set<ComponentModel> componentModels) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.vaadin.addon.cdiproperties.producer;\n");
        sb.append("import javax.enterprise.inject.*;\n");
        sb.append("import javax.inject.*;\n");
        sb.append("import org.vaadin.addon.cdiproperties.ComponentConfigurator;\n");
        sb.append("import javax.enterprise.inject.spi.*;\n");
        sb.append("import javax.enterprise.context.SessionScoped;\n");
        sb.append("import org.vaadin.addon.cdiproperties.annotation.*;\n");
        sb.append("\n\n@SessionScoped\n");
        // sb.append("@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD })\n");
        // sb.append("@Retention(RetentionPolicy.RUNTIME)\n");
        sb.append("public class ComponentProducers implements java.io.Serializable {\n\n");
        sb.append("@Inject\n");
        sb.append("private ComponentConfigurator cc;\n\n");

        List<ComponentModel> ordered = Lists.newArrayList(componentModels);
        Collections.sort(ordered, new Comparator<ComponentModel>() {
            @Override
            public int compare(ComponentModel o1, ComponentModel o2) {
                return o1.getComponentClass().getSimpleName()
                        .compareTo(o2.getComponentClass().getSimpleName());
            }
        });
        for (ComponentModel componentModel : ordered) {
            sb.append(componentModel.toProducerMethod());
        }

        sb.append("\n\n}");
        return sb.toString();
    }

    private static Object getPojoInstance(PojoClass pojoClass) {
        Object instance = null;
        try {
            instance = pojoClass.getClazz().newInstance();
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        }
        return instance;
    }

    public static void writeFile(String filename, String output)
            throws IOException {
        File file = new File(filename);
        FileWriter writer = new FileWriter(file);
        writer.write(output);
        writer.close();
    }

    static class ComponentModel {
        private final Class componentClass;
        private final Set<ComponentProperty> properties = Sets.newHashSet();

        public ComponentModel(Class componentClass) {
            super();
            this.componentClass = componentClass;
        }

        public Set<ComponentProperty> getProperties() {
            return properties;
        }

        public Class getComponentClass() {
            return componentClass;
        }

        public String toAnnotation() {
            StringBuilder sb = new StringBuilder();
            sb.append("package org.vaadin.addon.cdiproperties.annotation;\n");
            sb.append("import javax.inject.*;\n");
            sb.append("import java.lang.annotation.*;\n");
            sb.append("import javax.enterprise.util.*;\n");
            sb.append("\n\n@Qualifier\n");
            sb.append("@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD })\n");
            sb.append("@Retention(RetentionPolicy.RUNTIME)\n");
            sb.append("public @interface ").append(formatAnnotationClassName())
                    .append(" {");

            List<ComponentProperty> ordered = Lists.newArrayList(properties);
            Collections.sort(ordered, new Comparator<ComponentProperty>() {
                @Override
                public int compare(ComponentProperty o1, ComponentProperty o2) {
                    return o1.name.compareTo(o2.name);
                }

            });
            for (ComponentProperty cp : ordered) {
                sb.append("\n");
                sb.append(cp.toAnnotationMethod());
            }

            sb.append("\n\n}");
            return sb.toString();
        }

        public String formatAnnotationClassName() {
            return (componentClass == AbstractComponent.class ? ""
                    : componentClass.getSimpleName()) + "Properties";
        }

        public String toProducerMethod() {
            StringBuilder sb = new StringBuilder();

            sb.append("@Produces\n");
            sb.append("@").append(formatAnnotationClassName()).append("\n");
            sb.append("public ").append(componentClass.getName())
                    .append(" create").append(componentClass.getSimpleName())
                    .append("With").append(formatAnnotationClassName())
                    .append("(final InjectionPoint ip) throws Exception {\n");
            sb.append("\treturn cc.getComponent(")
                    .append(formatAnnotationClassName())
                    .append(".class, ip);\n");
            sb.append("}\n\n");
            return sb.toString();
        }

        static class ComponentProperty {
            private final String type;
            private final String name;
            private final String defaultValue;

            public ComponentProperty(String type, String name,
                    String defaultValue) {
                super();
                this.type = type;
                this.name = name;
                this.defaultValue = defaultValue;
            }

            String toAnnotationMethod() {
                StringBuilder sb = new StringBuilder();
                sb.append("\n@Nonbinding\n");
                sb.append(type).append(" ").append(name).append("() default ")
                        .append(defaultValue).append(";");
                return sb.toString();
            }

        }
    }

}
