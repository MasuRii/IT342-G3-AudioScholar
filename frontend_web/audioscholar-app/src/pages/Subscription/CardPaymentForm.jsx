import React, { useState } from 'react';

const CardPaymentForm = ({ onSubmit }) => {
    const [cardDetails, setCardDetails] = useState({
        cardNumber: '',
        expiryDate: '', // MM/YY format
        cvv: '',
        nameOnCard: ''
    });
    const [errors, setErrors] = useState({});

    const handleChange = (e) => {
        const { name, value } = e.target;
        let formattedValue = value;

        // Basic input formatting/validation
        if (name === 'cardNumber') {
            formattedValue = value.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').trim().slice(0, 19); // Format as XXXX XXXX XXXX XXXX
        } else if (name === 'expiryDate') {
            formattedValue = value.replace(/\D/g, '');
            if (formattedValue.length > 2) {
                formattedValue = formattedValue.slice(0, 2) + '/' + formattedValue.slice(2, 4);
            } else {
                formattedValue = formattedValue.slice(0, 2);
            }
        } else if (name === 'cvv') {
            formattedValue = value.replace(/\D/g, '').slice(0, 4);
        }

        setCardDetails(prev => ({ ...prev, [name]: formattedValue }));
        // Clear specific error on change
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: null }));
        }
    };

    const validateForm = () => {
        const newErrors = {};
        if (!cardDetails.cardNumber || cardDetails.cardNumber.replace(/\s/g, '').length < 13) newErrors.cardNumber = 'Valid card number required'; // Basic length check
        if (!cardDetails.expiryDate || !/^(0[1-9]|1[0-2])\/([0-9]{2})$/.test(cardDetails.expiryDate)) newErrors.expiryDate = 'Valid expiry date (MM/YY) required';
        if (!cardDetails.cvv || cardDetails.cvv.length < 3) newErrors.cvv = 'Valid CVV required';
        if (!cardDetails.nameOnCard.trim()) newErrors.nameOnCard = 'Name on card required';
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (validateForm()) {
            // Pass relevant (potentially masked) details up
            onSubmit({
                type: 'card',
                last4: cardDetails.cardNumber.slice(-4), // Only pass last 4 digits
                expiry: cardDetails.expiryDate,
                name: cardDetails.nameOnCard
            });
        }
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4">
            <div>
                <label htmlFor="cardNumber" className="block text-sm font-medium text-gray-700">Card Number</label>
                <input
                    type="text"
                    name="cardNumber"
                    id="cardNumber"
                    value={cardDetails.cardNumber}
                    onChange={handleChange}
                    placeholder="XXXX XXXX XXXX XXXX"
                    maxLength="19"
                    className={`mt-1 block w-full px-3 py-2 border ${errors.cardNumber ? 'border-red-500' : 'border-gray-300'} rounded-md shadow-sm focus:outline-none focus:ring-[#2D8A8A] focus:border-[#2D8A8A] sm:text-sm`}
                    required
                />
                {errors.cardNumber && <p className="mt-1 text-xs text-red-600">{errors.cardNumber}</p>}
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div>
                    <label htmlFor="expiryDate" className="block text-sm font-medium text-gray-700">Expiry Date</label>
                    <input
                        type="text"
                        name="expiryDate"
                        id="expiryDate"
                        value={cardDetails.expiryDate}
                        onChange={handleChange}
                        placeholder="MM/YY"
                        maxLength="5"
                        className={`mt-1 block w-full px-3 py-2 border ${errors.expiryDate ? 'border-red-500' : 'border-gray-300'} rounded-md shadow-sm focus:outline-none focus:ring-[#2D8A8A] focus:border-[#2D8A8A] sm:text-sm`}
                        required
                    />
                    {errors.expiryDate && <p className="mt-1 text-xs text-red-600">{errors.expiryDate}</p>}
                </div>
                <div>
                    <label htmlFor="cvv" className="block text-sm font-medium text-gray-700">CVV</label>
                    <input
                        type="text" // Use text to allow controlling length easily
                        name="cvv"
                        id="cvv"
                        value={cardDetails.cvv}
                        onChange={handleChange}
                        placeholder="123"
                        maxLength="4"
                        className={`mt-1 block w-full px-3 py-2 border ${errors.cvv ? 'border-red-500' : 'border-gray-300'} rounded-md shadow-sm focus:outline-none focus:ring-[#2D8A8A] focus:border-[#2D8A8A] sm:text-sm`}
                        required
                    />
                    {errors.cvv && <p className="mt-1 text-xs text-red-600">{errors.cvv}</p>}
                </div>
            </div>

            <div>
                <label htmlFor="nameOnCard" className="block text-sm font-medium text-gray-700">Name on Card</label>
                <input
                    type="text"
                    name="nameOnCard"
                    id="nameOnCard"
                    value={cardDetails.nameOnCard}
                    onChange={handleChange}
                    placeholder="Full Name"
                    className={`mt-1 block w-full px-3 py-2 border ${errors.nameOnCard ? 'border-red-500' : 'border-gray-300'} rounded-md shadow-sm focus:outline-none focus:ring-[#2D8A8A] focus:border-[#2D8A8A] sm:text-sm`}
                    required
                />
                {errors.nameOnCard && <p className="mt-1 text-xs text-red-600">{errors.nameOnCard}</p>}
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

export default CardPaymentForm; 