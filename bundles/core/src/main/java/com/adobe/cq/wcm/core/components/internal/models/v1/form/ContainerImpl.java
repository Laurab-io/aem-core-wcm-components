/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2017 Adobe Systems Incorporated
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package com.adobe.cq.wcm.core.components.internal.models.v1.form;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.servlet.ServletException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ContainerExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.adobe.cq.export.json.SlingModelFilter;
import com.adobe.cq.wcm.core.components.internal.Constants;
import com.adobe.cq.wcm.core.components.internal.Utils;
import com.adobe.cq.wcm.core.components.internal.form.FormConstants;
import com.adobe.cq.wcm.core.components.models.form.Container;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.foundation.forms.FormStructureHelper;
import com.day.cq.wcm.foundation.forms.FormStructureHelperFactory;
import com.day.cq.wcm.foundation.forms.FormsHelper;

import static com.day.cq.wcm.foundation.forms.FormsConstants.SCRIPT_FORM_SERVER_VALIDATION;

@Model(adaptables = SlingHttpServletRequest.class, adapters = {Container.class, ContainerExporter.class}, resourceType = ContainerImpl
        .RESOURCE_TYPE)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME, extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ContainerImpl implements Container, ContainerExporter {

    protected static final String RESOURCE_TYPE = FormConstants.RT_CORE_FORM_CONTAINER_V1;

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerImpl.class);
    private static final String PROP_METHOD_DEFAULT = "POST";
    private static final String PROP_ENCTYPE_DEFAULT = "multipart/form-data";
    private static final String INIT_SCRIPT = "init";

    @Self
    private SlingHttpServletRequest request;

    @ScriptVariable
    private SlingHttpServletResponse response;

    @ScriptVariable
    private Page currentPage;

    @ValueMapValue
    @Default(values = PROP_METHOD_DEFAULT)
    private String method;

    @ValueMapValue
    @Default(values = PROP_ENCTYPE_DEFAULT)
    private String enctype;

    @ValueMapValue
    @Default(values = "")
    private String id;

    @ValueMapValue(optional = true)
    private String actionType;

    @ValueMapValue(name = ResourceResolver.PROPERTY_RESOURCE_TYPE)
    @Default(values = "")
    private String dropAreaResourceType;

    @ValueMapValue(optional = true)
    private String redirect;

    private String name;
    private String action;
    private Map<String, ? extends ComponentExporter> childrenModels;
    private String[] exportedItemsOrder;

    @ScriptVariable
    private Resource resource;

    @OSGiService
    private FormStructureHelperFactory formStructureHelperFactory;

    @OSGiService
    private SlingModelFilter slingModelFilter;

    @OSGiService
    private ModelFactory modelFactory;

    @PostConstruct
    private void initModel() {
        FormStructureHelper formStructureHelper = formStructureHelperFactory.getFormStructureHelper(resource);
        request.setAttribute(FormsHelper.REQ_ATTR_FORM_STRUCTURE_HELPER, formStructureHelper);
        this.action = Utils.getURL(request, currentPage);
        if (StringUtils.isBlank(id)) {
            id = FormsHelper.getFormId(request);
        }
        this.name = id;
        this.dropAreaResourceType += "/new";
        if (redirect != null) {
            String contextPath = request.getContextPath();
            if (StringUtils.isNotBlank(contextPath) && redirect.startsWith("/")) {
                redirect = contextPath + redirect;
            }
        }

        if (!StringUtils.equals(request.getRequestPathInfo().getExtension(), Constants.EXPORTER_EXTENSION)) {
            runActionTypeInit(formStructureHelper);
        }
    }

    private void runActionTypeInit(FormStructureHelper formStructureHelper) {
        final RequestPathInfo requestPathInfo = request.getRequestPathInfo();
        if (response != null && !StringUtils.equals(requestPathInfo.getSelectorString(),
                SCRIPT_FORM_SERVER_VALIDATION) && StringUtils.isNotEmpty(actionType)) {
            final Resource formStart = formStructureHelper.getFormResource(request.getResource());
            try {
                FormsHelper.runAction(actionType, INIT_SCRIPT, formStart, request, response);
            } catch (IOException | ServletException e) {
                LOGGER.error("Unable to initialise form " + resource.getPath(), e);
            }
        }
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public String getAction() {
        return this.action;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getEnctype() {
        return this.enctype;
    }

    @Override
    public String getResourceTypeForDropArea() {
        return dropAreaResourceType;
    }

    @Override
    public String getRedirect() {
        return redirect;
    }

    @Nonnull
    @Override
    public Map<String, ? extends ComponentExporter> getExportedItems() {
        if (childrenModels == null) {
            childrenModels = getChildrenModels(request, ComponentExporter.class);
        }
        return childrenModels;
    }

    @Nonnull
    @Override
    public String[] getExportedItemsOrder() {
        if (exportedItemsOrder == null) {
            Map<String, ? extends ComponentExporter> models = getExportedItems();
            if (!models.isEmpty()) {
                exportedItemsOrder = models.keySet().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
            } else {
                exportedItemsOrder = ArrayUtils.EMPTY_STRING_ARRAY;
            }
        }
        return exportedItemsOrder;
    }

    @Nonnull
    @Override
    public String getExportedType() {
        return resource.getResourceType();
    }

    private <T> Map<String, T> getChildrenModels(@Nonnull SlingHttpServletRequest request, @Nonnull Class<T>
            modelClass) {
        Map<String, T> models = new LinkedHashMap<>();
        for (Resource child : slingModelFilter.filterChildResources(resource.getChildren())) {
            T model = modelFactory.getModelFromWrappedRequest(request, child, modelClass);
            if (model != null) {
                models.put(child.getName(), model);
            }
        }
        return models;
    }
}
