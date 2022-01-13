const dslWithSchema = require("../../../../../fixtures/jsonFormDslWithSchema.json");

const fieldPrefix = ".t--jsonformfield";
const propertyControlPrefix = ".t--property-control";
const backBtn = ".t--property-pane-back-btn";

describe("JSON Form Widget Form Bindings", () => {
  before(() => {
    cy.addDsl(dslWithSchema);
  });

  it("updates formData when field value changes", () => {
    const expectedInitialFormData = {
      age: 30,
      dob: "10/12/1992",
      migrant: false,
      address: { street: "Koramangala", city: "Bangalore" },
      hobbies: ["travelling", "swimming"],
      education: [{ college: "MIT", year: "20/10/2014" }],
      name: "John",
    };
    const updatedFormData = {
      age: 40,
      dob: "10/12/1992",
      migrant: false,
      address: { street: "Indranagar", city: "Bangalore" },
      hobbies: ["travelling"],
      education: [{ college: "IIT", year: "20/10/2014" }],
      name: "Test",
    };
    // Bind formData to Text1 widget text property
    cy.openPropertyPane("textwidget");
    cy.testJsontext("text", "{{JSON.stringify(JSONForm1.formData)}}");
    cy.closePropertyPane();

    // Validate initial form data
    cy.get(".t--draggable-textwidget")
      .find(".bp3-ui-text")
      .then(($el) => {
        const formData = JSON.parse($el.text());
        cy.wrap(formData).should("deep.equal", expectedInitialFormData);
      });

    // Modify form field values
    cy.get(`${fieldPrefix}-name input`)
      .clear({ force: true })
      .type(updatedFormData.name);
    cy.get(`${fieldPrefix}-age input`)
      .clear({ force: true })
      .clear({ force: true })
      .type(updatedFormData.age);
    cy.get(`${fieldPrefix}-address-street input`)
      .clear({ force: true })
      .type(updatedFormData.address.street);
    cy.get(`${fieldPrefix}-hobbies .rc-select-selection-item`)
      .contains("swimming")
      .siblings(".rc-select-selection-item-remove")
      .click({ force: true });
    cy.get(`${fieldPrefix}-education-0--college input`)
      .clear({ force: true })
      .type(updatedFormData.education[0].college)
      .wait(200);

    cy.wait(1000);
    // Check if modified text updates formData
    cy.get(".t--draggable-textwidget")
      .find(".bp3-ui-text")
      .then(($el) => {
        const formData = JSON.parse($el.text());
        cy.wrap(formData).should("deep.equal", updatedFormData);
      });
  });

  it("updates fieldState", () => {
    const expectedInitialFieldState = {
      name: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
      age: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
      dob: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
      migrant: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
      },
      address: {
        street: {
          isDisabled: false,
          isVisible: true,
          isRequired: false,
          isValid: true,
        },
        city: {
          isDisabled: false,
          isVisible: true,
          isRequired: false,
          isValid: true,
        },
      },
      education: [
        {
          college: {
            isDisabled: false,
            isVisible: true,
            isRequired: false,
            isValid: true,
          },
          year: {
            isDisabled: false,
            isVisible: true,
            isRequired: false,
            isValid: true,
          },
        },
      ],
      hobbies: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
    };

    const expectedUpdatedFieldState = {
      name: {
        isDisabled: false,
        isVisible: true,
        isRequired: true,
        isValid: false,
      },
      age: {
        isDisabled: true,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
      dob: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
      migrant: {
        isDisabled: false,
        isVisible: false,
        isRequired: false,
      },
      address: {
        street: {
          isDisabled: false,
          isVisible: true,
          isRequired: true,
          isValid: false,
        },
        city: {
          isDisabled: false,
          isVisible: true,
          isRequired: false,
          isValid: true,
        },
      },
      education: [
        {
          college: {
            isDisabled: false,
            isVisible: true,
            isRequired: true,
            isValid: false,
          },
          year: {
            isDisabled: false,
            isVisible: false,
            isRequired: false,
            isValid: true,
          },
        },
      ],
      hobbies: {
        isDisabled: false,
        isVisible: true,
        isRequired: false,
        isValid: true,
      },
    };

    cy.openPropertyPane("textwidget");
    cy.testJsontext("text", "{{JSON.stringify(JSONForm1.fieldState)}}");
    cy.closePropertyPane();

    cy.get(".t--draggable-textwidget")
      .find(".bp3-ui-text")
      .then(($el) => {
        const formData = JSON.parse($el.text());
        cy.wrap(formData).should("deep.equal", expectedInitialFieldState);
      });

    cy.openPropertyPane("jsonformwidget");

    // name.required -> true
    cy.openFieldConfiguration("name");
    cy.togglebar(`${propertyControlPrefix}-required input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);

    // age.disabled -> true
    cy.openFieldConfiguration("age");
    cy.togglebar(`${propertyControlPrefix}-disabled input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);

    // migrant.visible -> false
    cy.openFieldConfiguration("migrant");
    cy.togglebarDisable(`${propertyControlPrefix}-visible input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);

    // address.street.required -> true
    cy.openFieldConfiguration("address");
    cy.openFieldConfiguration("street");
    cy.togglebar(`${propertyControlPrefix}-required input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);

    // education.college.required -> true
    // education.year.visible -> false
    cy.openFieldConfiguration("education");
    cy.openFieldConfiguration("__array_item__");
    cy.openFieldConfiguration("college");
    cy.togglebar(`${propertyControlPrefix}-required input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);
    cy.openFieldConfiguration("year");
    cy.togglebarDisable(`${propertyControlPrefix}-visible input`);
    cy.get(backBtn)
      .click({ force: true })
      .wait(500);

    cy.closePropertyPane();

    cy.get(`${fieldPrefix}-name input`).clear({ force: true });
    cy.get(`${fieldPrefix}-address-street input`).clear({ force: true });
    cy.get(`${fieldPrefix}-address-city input`).clear({ force: true });
    cy.get(`${fieldPrefix}-education-0--college input`)
      .clear({ force: true })
      .wait(500);

    cy.wait(1000);

    cy.get(".t--draggable-textwidget")
      .find(".bp3-ui-text")
      .then(($el) => {
        const formData = JSON.parse($el.text());
        cy.wrap(formData).should("deep.equal", expectedUpdatedFieldState);
      });
  });
});