package org.vaadin.addon.cdiproperties;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import com.vaadin.data.util.BeanItem;
import com.vaadin.server.Sizeable;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.AbstractOrderedLayout;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;


@SuppressWarnings("serial")
@SessionScoped
public class ComponentConfigurator implements Serializable {

    public final static String IGNORED_STRING = "CDI_PROPERTIES_IGNORE";

    private static Annotation getPropertyAnnotation(InjectionPoint ip,
            Class annotationClass) {
        Annotation result = null;
        for (final Annotation annotation : ip.getQualifiers()) {
            if (annotationClass.isAssignableFrom(annotation.getClass())) {
                result = annotation;
                break;
            }
        }
        return result;
    }

    private static Object getPropertyValue(Object instance, String methodName) {
        Object result = null;
        try {
            result = instance.getClass().getMethod(methodName).invoke(instance);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return result;
    }

    public <T extends Component> T getComponent(
            Class<? extends Annotation> annotationClass, InjectionPoint ip)
            throws InstantiationException, IllegalAccessException {
        Annotation propertyAnnotation = getPropertyAnnotation(ip,
                annotationClass);
        Class<T> componentClass = (Class) getPropertyValue(propertyAnnotation,
                "implementation");
        Component component = componentClass.newInstance();

        // Apply the setters
        applyProperties(component, propertyAnnotation);

        // Apply custom properties
        Iterator<CustomProperty> iterator = customProperties.iterator();
        while (iterator.hasNext()) {
            CustomProperty customProperty = iterator.next();
            if (customProperty.appliesTo(component)) {
                customProperty.apply(component, propertyAnnotation);
            }
        }

        return (T) component;
    }

    @Inject
    private Instance<CustomProperty> customProperties;

    public static abstract class CustomProperty {
        abstract void apply(Component component, Annotation propertyAnnotation);

        abstract boolean appliesTo(Component component);
    }

    private static void applyProperties(Component component,
            Annotation propertyAnnotation) {
        final BeanItem bi = new BeanItem(component);

        for (Method method : propertyAnnotation.getClass().getMethods()) {
            try {
                Object value = method.invoke(propertyAnnotation);
                if (!IGNORED_STRING.equals(value)) {
                    bi.getItemProperty(method.getName()).setValue(value);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private static class CustomPropertySize extends CustomProperty {
        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            Boolean sizeFull = (Boolean) getPropertyValue(propertyAnnotation,
                    "sizeFull");
            Boolean sizeUndefined = (Boolean) getPropertyValue(
                    propertyAnnotation, "sizeUndefined");

            if (sizeFull) {
                component.setSizeFull();
            } else if (sizeUndefined) {
                component.setSizeUndefined();
            } else {
                String height = (String) getPropertyValue(propertyAnnotation,
                        "height");
                if (!IGNORED_STRING.equals(height)) {
                    component.setHeight(height);
                } else {
                    Float heightValue = (Float) getPropertyValue(
                            propertyAnnotation, "heightValue");
                    Sizeable.Unit unit = (Sizeable.Unit) getPropertyValue(
                            propertyAnnotation, "heightUnits");
                    component.setHeight(heightValue, unit);
                }

                String width = (String) getPropertyValue(propertyAnnotation,
                        "width");
                if (!IGNORED_STRING.equals(width)) {
                    component.setWidth(width);
                } else {
                    Float widthValue = (Float) getPropertyValue(
                            propertyAnnotation, "widthValue");
                    Sizeable.Unit unit = (Sizeable.Unit) getPropertyValue(
                            propertyAnnotation, "widthUnits");
                    component.setWidth(widthValue, unit);
                }
            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyCaptionKey extends CustomProperty {
        @Inject
        private Instance<TextBundle> textBundle;
        @Inject
        private Instance<Localizer> localizer;

        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String captionKey = (String) getPropertyValue(
                    propertyAnnotation, "captionKey");
            final Boolean localized = (Boolean) getPropertyValue(
                    propertyAnnotation, "localized");
            if (!IGNORED_STRING.equals(captionKey)) {
                try {
                    component.setCaption(textBundle.get().getText(captionKey));
                    if (localized) {
                        localizer.get().addLocalizedCaption(component,
                                captionKey);

                    }
                } catch (final UnsatisfiedResolutionException e) {
                    component.setCaption("No TextBundle implementation found!");
                }

            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyDescriptionKey extends CustomProperty {
        @Inject
        private Instance<TextBundle> textBundle;
        @Inject
        private Instance<Localizer> localizer;

        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String descriptionKey = (String) getPropertyValue(
                    propertyAnnotation, "descriptionKey");
            final Boolean localized = (Boolean) getPropertyValue(
                    propertyAnnotation, "localized");
            if (!IGNORED_STRING.equals(descriptionKey)) {
                TextField field = (TextField) component;
                try {
                    field.setDescription(textBundle.get().getText(descriptionKey));
                    if (localized) {
                        localizer.get().addLocalizedDescription(field,
                                                            descriptionKey);
                    }
                } catch (final UnsatisfiedResolutionException e) {
                    field.setDescription("No TextBundle implementation found!");
                }

            }
        }

        @Override
        boolean appliesTo(Component component) {
            return (component instanceof TextField);
        }
    }

    private static class CustomPropertyMargin extends CustomProperty {
        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            MarginInfo mi = null;
            final boolean[] margin = (boolean[]) getPropertyValue(
                    propertyAnnotation, "margin");
            if (margin.length == 1) {
                mi = new MarginInfo(margin[0]);
            } else if (margin.length == 2) {
                mi = new MarginInfo(margin[0], margin[1], margin[0], margin[1]);
            } else if (margin.length == 3) {
                mi = new MarginInfo(margin[0], margin[1], margin[2], margin[1]);
            } else if (margin.length == 4) {
                mi = new MarginInfo(margin[0], margin[1], margin[2], margin[3]);
            }

            if (mi != null) {
                if (component instanceof AbstractOrderedLayout) {
                    ((AbstractOrderedLayout) component).setMargin(mi);
                } else if (component instanceof GridLayout) {
                    ((GridLayout) component).setMargin(mi);
                }
            }
        }

        @Override
        boolean appliesTo(Component component) {
            return component instanceof AbstractOrderedLayout
                    || component instanceof GridLayout;
        }
    }

    private static class CustomPropertyStyleName extends CustomProperty {
        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String[] styleNames = (String[]) getPropertyValue(
                    propertyAnnotation, "styleName");
            for (String styleName : styleNames) {
                component.addStyleName(styleName);
                ;
            }
        }

        @Override
        boolean appliesTo(Component component) {
            return true;
        }
    }

    private static class CustomPropertyLabelValueKey extends CustomProperty {
        @Inject
        private Instance<TextBundle> textBundle;
        @Inject
        private Instance<Localizer> localizer;

        @Override
        void apply(Component component, Annotation propertyAnnotation) {
            final String valueKey = (String) getPropertyValue(
                    propertyAnnotation, "valueKey");
            if (!IGNORED_STRING.equals(valueKey)) {
                try {
                    ((Label) component).setValue(textBundle.get().getText(
                            valueKey));
                    final Boolean localized = (Boolean) getPropertyValue(
                            propertyAnnotation, "localized");
                    if (localized) {
                        localizer.get().addLocalizedLabelValue(
                                (Label) component, valueKey);
                    }
                } catch (final UnsatisfiedResolutionException e) {
                    component.setCaption("No TextBundle implementation found!");
                }

            }
        }

        @Override
        boolean appliesTo(Component component) {
            return component instanceof Label;
        }
    }
}
