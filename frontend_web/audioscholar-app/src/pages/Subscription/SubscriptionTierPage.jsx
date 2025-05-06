import React from 'react';
import { useNavigate } from 'react-router-dom';
import { FiCheckCircle } from 'react-icons/fi'; // Import the check icon
import { Header } from '../Home/HomePage';

const SubscriptionTierPage = () => {
    const navigate = useNavigate();

    // Plan data structure similar to HomePage
    const plans = [
        {
            name: "Basic",
            price: "$0",
            description: "Essential tools for efficient lecture capture.",
            features: [
                "Mobile Lecture Recording (Foreground)",
                "Audio Upload (Mobile & Web)",
                "Standard AI Summaries & Notes",
                "Basic YouTube Recommendations",
                "Local Storage"
            ],
            cta: "Current Plan", 
            isCurrent: true
        },
        {
            name: "Premium",
            price: "₱150",
            description: "Unlock advanced features & cloud power.",
            features: [
                "Includes all Basic features, plus:",
                "Background Mobile Recording",
                "Enhanced AI Summaries (e.g., w/ PPT Context)",
                "Expanded Learning Recommendations",
                "Optional Cloud Sync & Backup",
                "Priority Support (Future)"
            ],
            cta: "Choose Premium",
            featured: true,
            isCurrent: false
        }
    ];

    const handleSelectTier = (tier) => {
        console.log(`Selected tier: ${tier}`);
        if (tier === 'Premium') {
            localStorage.setItem('selectedTier', 'Premium'); 
            navigate('/payment');
        } else {
            alert('You are already on the Basic Free tier.');
        }
    };

    return (
        <div className="min-h-screen flex flex-col bg-gray-100">
            <title>AudioScholar - Choose Subscription</title>
            <Header />

            {/* Reverted background to light, adjusted text colors */}
            <main className="flex-grow flex items-center justify-center py-12 bg-gray-100"> 
                <div className="container mx-auto px-4">
                    {/* Adjusted title/subtitle colors for light background */}
                    <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-gray-800 mb-4">Choose Your Plan</h2>
                    <p className="font-inter font-normal text-lg leading-5 tracking-normal text-center text-gray-600 mb-12">Upgrade to unlock premium features.</p>

                    <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
                        {plans.map((plan, index) => (
                            <div
                                key={index}
                                // Adjusted card styles for light background
                                className={`relative p-8 rounded-lg shadow-lg border flex flex-col ${plan.featured ? 'border-teal-400 bg-white scale-105' : 'border-gray-200 bg-gray-50'} transition-transform duration-300`}
                            >
                                {plan.featured && (
                                    <div className="bg-teal-500 text-white font-inter font-medium text-[11px] leading-4 tracking-wide py-1 px-3 rounded-full inline-block mb-4 absolute -top-3 right-4">
                                        Recommended
                                    </div>
                                )}
                                {/* Adjusted text colors within cards */}
                                <h3 className={`font-montserrat font-semibold text-[24px] leading-[32px] tracking-normal mb-1 ${plan.featured ? 'text-[#1A365D]' : 'text-gray-800'}`}>{plan.name}</h3>
                                <p className={`font-inter font-normal text-sm mb-2 flex-grow ${plan.featured ? 'text-gray-600' : 'text-gray-500'}`}>{plan.description}</p>
                                <p className={`text-3xl font-bold mb-6 ${plan.featured ? 'text-teal-600' : 'text-gray-900'}`}>{plan.price}<span className="text-sm font-normal text-gray-500">{plan.name === 'Premium' ? '/month' : ''}</span></p>
                                <ul className="my-6 space-y-3">
                                    {plan.features.map((feature, i) => (
                                        <li key={i} className={`flex items-start font-inter font-normal text-sm leading-5 tracking-normal ${plan.featured ? 'text-gray-700' : 'text-gray-600'}`}>
                                            <FiCheckCircle className={`h-5 w-5 mr-2 ${plan.featured ? 'text-teal-500' : 'text-gray-400'} flex-shrink-0 mt-0.5`} />
                                            {feature}
                                        </li>
                                    ))}
                                </ul>
                                <button 
                                    onClick={() => handleSelectTier(plan.name)}
                                    // Adjusted button styles for light background
                                    className={`mt-auto w-full text-center py-3 px-4 rounded-md font-inter font-medium text-sm leading-5 tracking-tight transition-colors duration-200 transform hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-offset-2 ${plan.isCurrent 
                                        ? 'bg-gray-200 text-gray-700 cursor-default focus:ring-gray-400' // Disabled style for current plan
                                        : plan.featured 
                                            ? 'bg-teal-500 hover:bg-teal-600 text-white shadow-md focus:ring-teal-400' // Premium button style
                                            : 'bg-white hover:bg-gray-100 text-teal-600 border border-teal-500 focus:ring-teal-300' // Fallback style (if not current and not featured)
                                    }`}
                                    disabled={plan.isCurrent} // Disable button for the current plan
                                >
                                    {plan.cta}
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            </main>
        </div>
    );
};

export default SubscriptionTierPage; 