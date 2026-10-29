// Keep Springdoc's actual groups, operation URLs and primary business group.
window.addEventListener('load', () => {
  window.ui = SwaggerUIBundle({
    configUrl: '/v3/api-docs/swagger-config',
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: 'StandaloneLayout',
    syntaxHighlight: { activated: true, theme: 'agate' },
  });
});
