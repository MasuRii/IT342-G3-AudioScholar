import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Header } from '../Home/HomePage';

const CheckoutPage = () => {
    const [tier, setTier] = useState(null);
    const [paymentDetails, setPaymentDetails] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        // Retrieve selected tier and payment details from localStorage
        const selectedTier = localStorage.getItem('selectedTier');
        const details = localStorage.getItem('paymentDetails');

        if (!selectedTier || !details) {
            // If data is missing, redirect back to subscription selection
            console.error('Missing subscription or payment details. Redirecting...');
            navigate('/subscribe'); 
            return;
        }

        setTier(selectedTier);
        try {
            setPaymentDetails(JSON.parse(details));
        } catch (error) {
            console.error('Error parsing payment details:', error);
            navigate('/payment'); // Redirect if details are corrupted
        }

    }, [navigate]);

    const handleConfirm = () => {
        setIsLoading(true);
        console.log('Confirming subscription...', { tier, paymentDetails });

        // --- Mock Backend Call ---
        // Simulate API call delay
        setTimeout(() => {
            console.log('Subscription Confirmed (Mocked)!');
            
            // Mock successful payment: Update user status (e.g., in localStorage)
            localStorage.setItem('userSubscriptionTier', tier); // Set user tier to Premium
            
            // Clean up temporary storage
            localStorage.removeItem('selectedTier');
            localStorage.removeItem('paymentDetails');

            setIsLoading(false);

            // Redirect to dashboard or profile page
            // Potentially show a success message first
            alert('Subscription successful! You are now a Premium member.');
            navigate('/profile'); // Redirect to profile page

        }, 1500); // Simulate 1.5 seconds delay
    };

    const getPaymentDisplay = () => {
        if (!paymentDetails) return 'N/A';

        if (paymentDetails.type === 'card') {
            return `Card ending in ${paymentDetails.last4}, expires ${paymentDetails.expiry}`;
        } else if (paymentDetails.type === 'ewallet') {
            // Use masked number if available, otherwise show full (though masking is better)
            return `E-Wallet: ${paymentDetails.maskedNumber || paymentDetails.number}`;
        }
        return 'Unknown Payment Method';
    };

    const getPrice = () => {
        // In a real app, fetch this based on the tier
        // Updated price for Premium
        return tier === 'Premium' ? '₱150 / month' : 'Free';
    }

    if (!tier || !paymentDetails) {
        // Show loading or placeholder while retrieving data
        return (
            <div className="min-h-screen flex items-center justify-center bg-gray-100">
                 <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-teal-500"></div>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex flex-col bg-gray-100">
            <title>AudioScholar - Checkout</title>
            <Header />

            <main className="flex-grow flex items-center justify-center py-12">
                <div className="container mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="max-w-lg mx-auto bg-white p-8 rounded-lg shadow-xl">
                        <h1 className="text-2xl font-bold text-gray-800 mb-6 text-center">Confirm Your Subscription</h1>

                        <div className="space-y-4 mb-8">
                            <div className="flex justify-between items-center border-b pb-2">
                                <span className="text-gray-600 font-medium">Plan:</span>
                                <span className="text-gray-800 font-semibold text-lg">{tier}</span>
                            </div>
                            <div className="flex justify-between items-center border-b pb-2">
                                <span className="text-gray-600 font-medium">Price:</span>
                                <span className="text-gray-800 font-semibold text-lg">{getPrice()}</span>
                            </div>
                            <div className="flex justify-between items-center border-b pb-2">
                                <span className="text-gray-600 font-medium">Payment Method:</span>
                                <span className="text-gray-800 text-sm text-right">{getPaymentDisplay()}</span>
                            </div>
                        </div>

                        <button 
                            onClick={handleConfirm}
                            disabled={isLoading}
                            className={`w-full py-3 px-4 rounded-md shadow-sm text-sm font-medium text-white transition-colors duration-200 transform hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A] ${
                                isLoading 
                                ? 'bg-gray-400 cursor-not-allowed' 
                                : 'bg-[#2D8A8A] hover:bg-[#236b6b]'
                            }`}
                        >
                            {isLoading ? (
                                <span className="flex items-center justify-center">
                                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                    </svg>
                                    Processing...
                                </span>
                            ) : (
                                'Confirm and Pay'
                            )}
                        </button>

                        <button 
                            onClick={() => navigate('/payment')} // Go back to payment method selection
                            disabled={isLoading}
                            className="w-full mt-3 py-2 px-4 rounded-md text-sm font-medium text-gray-600 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-300 transition-colors duration-200"
                        >
                            Change Payment Method
                        </button>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default CheckoutPage; 