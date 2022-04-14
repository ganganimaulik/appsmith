const apiwidget = require("../../../../locators/apiWidgetslocator.json");
const datasourceFormData = require("../../../../fixtures/datasources.json");
const datasourceEditor = require("../../../../locators/DatasourcesEditor.json");

describe("Authenticated API Datasource", function() {
  const URL = datasourceFormData["authenticatedApiUrl"];

  it("1. Bug: 12045 - No Blank screen diplay after New Authentication API datasource creation", function() {
    cy.NavigateToAPI_Panel();
    cy.get(apiwidget.createAuthApiDatasource).click({ force: true });
    cy.wait("@createDatasource").should(
      "have.nested.property",
      "response.body.responseMeta.status",
      201,
    );
    cy.renameDatasource("FakeAuthenticatedApi");
    cy.fillAuthenticatedAPIForm();
    cy.saveDatasource();
    cy.contains(URL);
  });

  it("2. Bug: 12045 - No Blank screen diplay after editing/opening existing Authentication API datasource", function() {
    cy.xpath("//span[text()='EDIT']/parent::a").click();
    cy.get(datasourceEditor.url).type("/users");
    cy.saveDatasource();
    cy.contains(URL + "/users");
    cy.deleteDatasource("FakeAuthenticatedApi");
  });
});
