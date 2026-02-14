const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

async function startCheckout() {
    try {
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: 'NL',
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            // Step 10 - Add the onSubmit handler. Use /api/subscription-create when subscription mode is selected (zero-auth to tokenize card).
            onSubmit: async (state, component, actions) => {
                console.info("onSubmit", state, component, actions);
                try {
                    if (state.isValid) {
                        const endpoint = document.getElementById("subscription").checked ? "/api/subscription-create" : "/api/payments";
                        const response = await fetch(endpoint, {
                            method: "POST",
                            body: state.data ? JSON.stringify(state.data) : "",
                            headers: {
                                "Content-Type": "application/json",
                            }
                        });
                        const data = await response.json();
                        const { action, order, resultCode } = data;

                        if (!resultCode) {
                            console.warn("reject", data);
                            actions.reject();
                        }

                        actions.resolve({
                            resultCode,
                            action,
                            order
                        });
                    }
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                handleOnPaymentCompleted(result, component);
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                handleOnPaymentFailed(result, component);
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                window.location.href = "/result/error";
            }
        };

        // Optional configuration for cards
        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                amount: {
                    value: 9998,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: '1234 5678 9012 3456',
                    expiryDate: 'MM/YY',
                    securityCodeThreeDigits: '123',
                    securityCodeFourDigits: '1234',
                    holderName: 'Developer Relations Team'
                }
            }
        };

        // Start the AdyenCheckout and mount the element onto the `payment`-div.
        const adyenCheckout = await AdyenCheckout(configuration);
        const dropin = new Dropin(adyenCheckout, { paymentMethodsConfiguration: paymentMethodsConfiguration }).mount(document.getElementById("payment"));
    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

// Step 10 - Function to handle payment completion redirects
function handleOnPaymentCompleted(response) {
    switch (response.resultCode) {
        case "Authorised":
            window.location.href = "/result/success";
            break;
        case "Pending":
        case "Received":
            window.location.href = "/result/pending";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

// Step 10 - Function to handle payment failure redirects
function handleOnPaymentFailed(response) {
    switch (response.resultCode) {
        case "Cancelled":
        case "Refused":
            window.location.href = "/result/failed";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

startCheckout();