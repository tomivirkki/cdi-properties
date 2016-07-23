package org.vaadin.addon.cdiproperties;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.inject.Inject;
import javax.inject.Qualifier;

import com.vaadin.cdi.UIScoped;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.AbstractComponent;

@SuppressWarnings("serial")
@UIScoped
public class Localizer implements Serializable {

    @Inject
    private Instance<TextBundle> textBundle;

    private final Map<Component, String> localizedCaptions = new HashMap<Component, String>();
    private final Map<Label, String> localizedLabelValues = new HashMap<Label, String>();
    private final Map<AbstractComponent, String> localizedDescriptions = new HashMap<AbstractComponent, String>();

    void updateCaption(@Observes @TextBundleUpdated final Object parameters) {
        for (final Entry<Component, String> entry : localizedCaptions
                .entrySet()) {
            try {
                entry.getKey().setCaption(
                        textBundle.get().getText(entry.getValue()));
            } catch (final UnsatisfiedResolutionException e) {
                entry.getKey()
                        .setCaption("No TextBundle implementation found!");
            }
        }

        for (final Entry<Label, String> entry : localizedLabelValues.entrySet()) {
            try {
                entry.getKey().setValue(
                        textBundle.get().getText(entry.getValue()));
            } catch (final UnsatisfiedResolutionException e) {
                entry.getKey()
                        .setCaption("No TextBundle implementation found!");
            }
        }

        for (final Entry<AbstractComponent, String> entry : localizedDescriptions.entrySet()) {
            try {
                entry.getKey().setValue(
                        textBundle.get().getText(entry.getValue()));
            } catch (final UnsatisfiedResolutionException e) {
                entry.getKey()
                        .setDescription("No TextBundle implementation found!");
            }
        }
    }

    void addLocalizedCaption(final Component component, final String captionKey) {
        localizedCaptions.put(component, captionKey);
    }

    void addLocalizedLabelValue(final Label label, final String labelValueKey) {
        localizedLabelValues.put(label, labelValueKey);
    }

    void addLocalizedDescription(final AbstractComponent field, final String descriptionKey) {
        localizedDescriptions.put(field, descriptionKey);
    }

    @Qualifier
    @Target({ ElementType.PARAMETER, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TextBundleUpdated {
    }
}
