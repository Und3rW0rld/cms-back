package com.cms.adapters.out.web.mapper;

import com.cms.adapters.in.web.dto.response.DraftSiteResponseDTO;
import com.cms.domain.exception.ConversionException;
import com.cms.domain.model.site.DraftSite;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SiteWebMapper {
    private final ObjectMapper mapper;

    public DraftSiteResponseDTO toDraftSiteResponse (DraftSite site) {
        JsonNode content = toJsonNode(site.content().rawJson());
        return new DraftSiteResponseDTO(content, site.version(), site.updatedAt());
    }

    private JsonNode toJsonNode (String rawJson) {
        try {
            return mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new ConversionException("Failed to convert raw JSON to JsonNode");
        }
    }

}
