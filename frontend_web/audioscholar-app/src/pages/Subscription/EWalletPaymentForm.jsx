import React, { useState } from 'react';

const EWalletPaymentForm = ({ onSubmit }) => {
    const [phoneNumber, setPhoneNumber] = useState('');
    const [error, setError] = useState(null);
    const countryCode = '+63'; // Philippines country code

    const handleChange = (e) => {
        let value = e.target.value.replace(/\D/g, ''); // Remove non-digits
        // Limit to 10 digits after country code (standard PH mobile length)
        if (value.length > 10) {
            value = value.slice(0, 10);
        }
        setPhoneNumber(value);
        // Clear error on change
        if (error) {
            setError(null);
        }
    };

    const validateForm = () => {
        // Validate for 10 digits (e.g., 9XXXXXXXXX)
        if (phoneNumber.length !== 10 || !/^9\d{9}$/.test(phoneNumber)) {
            setError('Please enter a valid 10-digit Philippine mobile number (starting with 9).');
            return false;
        }
        setError(null);
        return true;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (validateForm()) {
            const fullNumber = `${countryCode}${phoneNumber}`;
            console.log('E-Wallet Number Submitted:', fullNumber);
            // Pass relevant details up
            onSubmit({
                type: 'ewallet',
                number: fullNumber,
                // Mask the number for display if needed later
                maskedNumber: `${countryCode}9******${phoneNumber.slice(-3)}` 
            });
        }
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4">
            <div>
                <label htmlFor="phoneNumber" className="block text-sm font-medium text-gray-700">Mobile Number (GCash/PayMaya)</label>
                <div className="mt-1 flex rounded-md shadow-sm">
                    <span className="inline-flex items-center px-3 rounded-l-md border border-r-0 border-gray-300 bg-gray-50 text-gray-500 sm:text-sm">
                        {countryCode}
                    </span>
                    <input
                        type="tel" // Use tel type for phone numbers
                        name="phoneNumber"
                        id="phoneNumber"
                        value={phoneNumber}
                        onChange={handleChange}
                        placeholder="9XXXXXXXXX"
                        maxLength="10"
                        className={`flex-1 min-w-0 block w-full px-3 py-2 rounded-none rounded-r-md border ${error ? 'border-red-500' : 'border-gray-300'} focus:outline-none focus:ring-[#2D8A8A] focus:border-[#2D8A8A] sm:text-sm`}
                        required
                    />
                </div>
                {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
                 <p className="mt-1 text-xs text-gray-500">Enter your 10-digit number (e.g., 9171234567).</p>
            </div>

            <button 
                type="submit"
                className="w-full mt-6 py-3 px-4 rounded-md shadow-sm text-sm font-medium text-white bg-[#2D8A8A] hover:bg-[#236b6b] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A] transition-colors duration-200 transform hover:-translate-y-0.5"
            >
                Proceed to Checkout
            </button>
        </form>
    );
};

export default EWalletPaymentForm; 