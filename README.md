# CDI Properties

CDI Properties contains individual annotations for each Vaadin core component that allow you to define a set of properties right above the injection point.

...so instead of:

    TextField textField = new TextField("Name");
    textField.setMaxLength(15);
    textField.setNullRepresentation("");
    textField.setWidth("100%");

..you can:

    @Inject
    @TextFieldProperties(caption = "Name", maxLength = 15, nullRepresentation = "", width = "100%")
    private TextField textField;



Use properties "captionKey" and "labelValueKey" to assign text values provided by your own TextBundle bean. Fire a @TextBundleUpdated event to utilize the built-in i18n functionality allowing you to change your applications language run-time.

[Link to an example project](https://github.com/tomivirkki/cdiutils-addressbook)

**The add-on can only be used in [Vaadin CDI](https://vaadin.com/addon/vaadin-cdi) enabled projects.**