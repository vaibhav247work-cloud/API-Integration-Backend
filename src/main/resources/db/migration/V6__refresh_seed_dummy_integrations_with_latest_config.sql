UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"$.data.orders",
      "recordPathType":"JSON_PATH",
      "filterByWindow":true,
      "recordDatePath":"$.orderDate",
      "recordDatePathType":"JSON_PATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["ORDER_ID"],
        "defaultAction":"KEEP_FIRST",
        "fieldActions":{
          "TOTAL_AMOUNT":"SUM"
        }
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-orders",
        "method":"GET",
        "url":"/orders",
        "queryParams":{
          "businessDate":"${requestDate}",
          "page":"${page}"
        },
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "requestWindowMode":"SINGLE_DATE",
        "requestDateVariable":"requestDate",
        "requestDateFormat":"yyyy-MM-dd",
        "paginate":true,
        "dataStep":true,
        "responseAlias":"orders"
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - No Auth JSON Local'
  AND base_url = 'https://dummy-no-auth.example.com/api';

UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"$.data.students",
      "recordPathType":"JSON_PATH",
      "filterByWindow":true,
      "recordDatePath":"$.enrollmentDate",
      "recordDatePathType":"JSON_PATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["STUDENT_ID"],
        "defaultAction":"KEEP_FIRST"
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-students",
        "method":"GET",
        "url":"/students",
        "queryParams":{
          "campus":"north",
          "fromDate":"${windowStartDate}",
          "toDate":"${windowEndDateExclusive}"
        },
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "requestWindowMode":"DATE_RANGE",
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - Basic JSON Local'
  AND base_url = 'https://dummy-basic.example.com/api';

UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"//Employees/Employee",
      "recordPathType":"XPATH",
      "filterByWindow":true,
      "recordDatePath":"./PayrollDate/text()",
      "recordDatePathType":"XPATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["EMPLOYEE_ID"],
        "defaultAction":"KEEP_FIRST",
        "fieldActions":{
          "GROSS_AMOUNT":"SUM"
        }
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-employees-xml",
        "method":"GET",
        "url":"/employees",
        "queryParams":{
          "payDate":"${requestDate}"
        },
        "requestFormat":"XML",
        "responseFormat":"XML",
        "requestWindowMode":"SINGLE_DATE",
        "requestDateVariable":"requestDate",
        "requestDateFormat":"yyyy-MM-dd",
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - API Key Header XML FTP'
  AND base_url = 'https://dummy-header-xml.example.com/service';

UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"$.items",
      "recordPathType":"JSON_PATH",
      "filterByWindow":true,
      "recordDatePath":"$.itemDate",
      "recordDatePathType":"JSON_PATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["ITEM_CODE"],
        "defaultAction":"KEEP_FIRST",
        "fieldActions":{
          "EXTENDED_PRICE":"SUM"
        }
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-items",
        "method":"GET",
        "url":"/inventory/items",
        "queryParams":{
          "warehouse":"WH01"
        },
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "requestWindowMode":"NONE",
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - API Key Query JSON Local'
  AND base_url = 'https://dummy-query-key.example.com/api';

UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"//*[local-name()=''Envelope'']/*[local-name()=''Body'']/*[local-name()=''Invoices'']/*[local-name()=''Invoice'']",
      "recordPathType":"XPATH",
      "filterByWindow":true,
      "recordDatePath":"./InvoiceDate/text()",
      "recordDatePathType":"XPATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["INVOICE_ID"],
        "defaultAction":"KEEP_FIRST",
        "fieldActions":{
          "GRAND_TOTAL":"SUM"
        }
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-invoices-soap",
        "method":"POST",
        "url":"/invoiceService",
        "bodyTemplate":"<Envelope><Body><GetInvoices><fromDate>${windowStartDate}</fromDate><toDate>${windowEndDateExclusive}</toDate></GetInvoices></Body></Envelope>",
        "requestFormat":"SOAP",
        "responseFormat":"SOAP",
        "requestWindowMode":"DATE_RANGE",
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - Bearer Static SOAP S3'
  AND base_url = 'https://dummy-soap-bearer.example.com/services';

UPDATE integration_definition
SET auth_config = CAST('{
      "type":"TOKEN_API",
      "method":"POST",
      "tokenUrl":"https://dummy-token-api.example.com/oauth/token",
      "headers":{
        "Content-Type":"application/json"
      },
      "bodyTemplate":"{\\\"clientId\\\":\\\"demo-client\\\",\\\"brand\\\":\\\"${brandCode}\\\"}",
      "requestFormat":"JSON",
      "responseFormat":"JSON",
      "tokenPath":"$.access_token",
      "tokenPathType":"JSON_PATH",
      "tokenHeaderName":"Authorization",
      "tokenPrefix":"Bearer "
    }' AS JSON),
    response_config = CAST('{
      "recordPath":"$.data.transactions",
      "recordPathType":"JSON_PATH",
      "filterByWindow":true,
      "recordDatePath":"$.transactionDate",
      "recordDatePathType":"JSON_PATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["TRANSACTION_ID"],
        "defaultAction":"KEEP_FIRST",
        "fieldActions":{
          "NET_PLUS_FEE":"SUM"
        }
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"start-session",
        "method":"POST",
        "url":"/sessions",
        "bodyTemplate":"{\\\"businessDate\\\":\\\"${businessDate}\\\"}",
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "dataStep":false,
        "responseVariables":{
          "$.sessionId":"sessionId",
          "$.storeCode":"storeCode"
        },
        "responseVariablePathType":"JSON_PATH",
        "responseAlias":"session"
      },
      {
        "orderIndex":2,
        "name":"fetch-transactions",
        "method":"GET",
        "url":"/transactions",
        "queryParams":{
          "sessionId":"${sessionId}",
          "businessDate":"${requestDate}"
        },
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "requestWindowMode":"SINGLE_DATE",
        "requestDateVariable":"requestDate",
        "requestDateFormat":"yyyy-MM-dd",
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - Token API JSON Local'
  AND base_url = 'https://dummy-token-api.example.com/api';

UPDATE integration_definition
SET response_config = CAST('{
      "recordPath":"$.customers",
      "recordPathType":"JSON_PATH",
      "filterByWindow":true,
      "recordDatePath":"$.customer.createdDate",
      "recordDatePathType":"JSON_PATH",
      "recordDateFormat":"yyyy-MM-dd",
      "duplicateHandling":{
        "enabled":true,
        "keyHeaders":["CUSTOMER_ID"],
        "defaultAction":"KEEP_FIRST"
      }
    }' AS JSON),
    step_config = CAST('[
      {
        "orderIndex":1,
        "name":"fetch-customers",
        "method":"GET",
        "url":"/customers",
        "queryParams":{
          "updatedFrom":"${windowStartDate}"
        },
        "requestFormat":"JSON",
        "responseFormat":"JSON",
        "requestWindowMode":"DATE_RANGE",
        "paginate":true,
        "dataStep":true
      }
    ]' AS JSON)
WHERE client_name = 'Seed Demo - OAuth2 Client Credentials JSON Local'
  AND base_url = 'https://dummy-oauth.example.com/api';
