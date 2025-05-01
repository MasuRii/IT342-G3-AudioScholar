import React, { useEffect, useState } from 'react';
import { FiGrid, FiLogIn, FiLogOut, FiMic, FiUpload, FiUser, FiUserPlus } from 'react-icons/fi';
import { Link, useNavigate } from 'react-router-dom';

export const Header = () => {
    const navigate = useNavigate();
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    useEffect(() => {
        const token = localStorage.getItem('AuthToken');
        setIsAuthenticated(!!token);
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('AuthToken');
        localStorage.removeItem('userId');
        setIsAuthenticated(false);
        navigate('/');
    };

    return (
        <header className="bg-[#1A365D] shadow-sm py-3">
            <div className="container mx-auto px-4 flex justify-between items-center">
                <Link to={isAuthenticated ? "/dashboard" : "/"} className="flex items-center gap-3">
                    <img src="/AudioScholar - No BG.png" alt="AudioScholar Logo" className="h-14 w-auto" />
                    <span className="font-montserrat font-semibold font-bold text-[24px] leading-[32px] tracking-normal text-white">AudioScholar</span>
                </Link>
                <nav className="hidden md:flex items-center space-x-2">
                    {isAuthenticated ? (
                        <>
                            <Link to="/dashboard" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiGrid className="w-4 h-4" /> Dashboard
                            </Link>
                            <Link to="/recordings" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiMic className="w-4 h-4" /> Recordings
                            </Link>
                            <Link to="/upload" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiUpload className="w-4 h-4" /> Upload
                            </Link>
                            <Link to="/profile" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiUser className="w-4 h-4" /> Profile
                            </Link>
                            <button
                                onClick={handleLogout}
                                className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md bg-transparent border-none cursor-pointer"
                            >
                                <FiLogOut className="w-4 h-4" /> Logout
                            </button>
                        </>
                    ) : (
                        <>
                            <Link to="/signin" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiLogIn className="w-4 h-4" /> Sign in
                            </Link>
                            <Link to="/signup" className="flex items-center gap-1.5 font-inter font-medium text-sm leading-5 tracking-tight text-white hover:text-indigo-600 hover:bg-white transition-all duration-200 px-3 py-1.5 rounded-md">
                                <FiUserPlus className="w-4 h-4" /> Get Started
                            </Link>
                        </>
                    )}
                </nav>
                <button className="md:hidden text-gray-300 hover:text-indigo-400">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                    </svg>
                </button>
            </div>
        </header>
    );
};

const HeroSection = () => {
    return (
        <section className="py-16 bg-gradient-to-r from-primary-50 to-primary-100">
            <div className="container mx-auto px-4 text-center">
                <h1 className="font-montserrat font-bold text-[45px] leading-[52px] tracking-normal text-gray-800 mb-6">
                    AudioScholar
                </h1>
                <p className="font-montserrat font-semibold text-[24px] leading-[32px] tracking-normal text-gray-600 max-w-2xl mx-auto">
                    Transform your audio experience with AI-powered solutions
                </p>
                <button className="mt-8 bg-primary-500 hover:bg-primary-600 text-white font-inter font-medium text-sm leading-5 tracking-tight py-3 px-8 rounded-md transition-colors shadow-md">
                    Get Started
                </button>
            </div>
        </section>
    );
};

const Features = () => {
    const features = [
        {
            title: "Smart Recording",
            description: (
                <>
                    Record lectures with intelligent<br />
                    background processing and offline<br />
                    support. Never worry about<br />
                    missing content again.
                </>
            )
        },
        {
            title: "AI-Powered Summaries",
            description: "Get instant, well-structured summaries of your recorded lectures using advanced AI technology."
        },
        {
            title: "Cross-Device Sync",
            description: "Access your recordings and summaries seamlessly across all your devices with cloud synchronization."
        }
    ];

    return (
        <section className="py-16 bg-teal-500">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-white mb-12">Why Choose AudioScholar?</h2>
                <div className="grid md:grid-cols-3 gap-8">
                    {features.map((feature, index) => (
                        <div key={index} className="bg-white p-6 rounded-lg border-2 border-teal-100 hover:border-teal-300 transition-all shadow-sm hover:shadow-md">
                            <div className="w-12 h-12 bg-teal-50 rounded-full flex items-center justify-center mb-4">
                                <div className="w-8 h-8 bg-teal-500 text-white rounded-full flex items-center justify-center font-inter font-medium text-xs leading-4 tracking-wide">
                                    {index + 1}
                                </div>
                            </div>
                            <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-teal-600 mb-3">{feature.title}</h3>
                            <p className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-600 whitespace-pre-line">
                                {typeof feature.description === 'string' ? feature.description : feature.description}
                            </p>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
};

const Testimonials = () => {
    const testimonials = [
        {
            quote: "AudioScholar has completely transformed the user's voice in practice. The user receives some incredibly convenient and trusted examples of shared access.",
            author: "Illustration",
            link: "https://www.youtube.com/watch?v=JXQW",
            role: "Content Creator"
        },
        {
            quote: "This background recording volume is an open-source format. I can have a variety of online formats such as streaming, mobile storage, etc.",
            author: "Michael Clark",
            link: "https://www.microsoft.com/",
            role: "Developer"
        },
        {
            quote: "The sound device used is perfect. I can record this key device and receive the same message from my own site.",
            author: "Linda Brown",
            link: "https://www.linda-brown.com",
            role: "Audio Engineer"
        }
    ];

    return (
        <section className="py-16 bg-white">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-gray-800 mb-12">What Our Users Say</h2>
                <div className="grid md:grid-cols-3 gap-8">
                    {testimonials.map((testimonial, index) => (
                        <div key={index} className="bg-white p-6 rounded-lg border border-gray-200 shadow-sm">
                            <div className="text-accent-400 mb-4">
                                {[...Array(5)].map((_, i) => (
                                    <span key={i}>★</span>
                                ))}
                            </div>
                            <p className="font-inter font-normal text-base leading-6 tracking-wide italic text-gray-600 mb-4">"{testimonial.quote}"</p>
                            <div>
                                <a href={testimonial.link} className="text-primary-600 hover:underline font-inter font-medium text-sm leading-5 tracking-tight">
                                    {testimonial.author}
                                </a>
                                <p className="font-inter font-normal text-xs leading-4 tracking-normal text-gray-500">{testimonial.role}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
};

const Pricing = () => {
    const plans = [
        {
            name: "Free",
            price: "$0",
            features: [
                "A basic recording structure",
                "A single printer summary",
                "A cloud storage story"
            ],
            cta: "Get Started"
        },
        {
            name: "Premium",
            price: "$9.99",
            features: [
                "A telephone recording",
                "A telephone numbering",
                "A document processing",
                "Clouds synchronization",
                "Advanced AI literature"
            ],
            cta: "Buy Now",
            featured: true
        }
    ];

    return (
        <section className="py-16 bg-teal-500">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-white mb-4">Pricing Plans</h2>
                <p className="font-inter font-normal text-sm leading-5 tracking-normal text-center text-white/80 mb-12">Inventory • Account</p>

                <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
                    {plans.map((plan, index) => (
                        <div
                            key={index}
                            className={`p-8 rounded-lg shadow-sm border-2 ${plan.featured ? 'border-teal-500 bg-white' : 'border-gray-200 bg-white'}`}
                        >
                            {plan.featured && (
                                <div className="bg-teal-500 text-white font-inter font-medium text-[11px] leading-4 tracking-wide py-1 px-3 rounded-full inline-block mb-4">
                                    Popular
                                </div>
                            )}
                            <h3 className="font-montserrat font-semibold text-[24px] leading-[32px] tracking-normal mb-1">{plan.name} <span className="text-teal-600">{plan.price}</span></h3>
                            <ul className="my-6 space-y-3">
                                {plan.features.map((feature, i) => (
                                    <li key={i} className="flex items-start font-inter font-normal text-sm leading-5 tracking-normal">
                                        <svg className="h-5 w-5 mr-2 text-teal-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                        </svg>
                                        {feature}
                                    </li>
                                ))}
                            </ul>
                            <button className={`w-full py-3 px-4 rounded-md font-inter font-medium text-sm leading-5 tracking-tight transition-colors ${plan.featured ? 'bg-teal-500 hover:bg-teal-600 text-white' : 'bg-gray-100 hover:bg-gray-200 text-gray-800'}`}>
                                {plan.cta}
                            </button>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
};

export const Footer = () => {
    return (
        <footer className="bg-gray-900 text-white pt-16 pb-8">
            <div className="container mx-auto px-4">
                <div className="grid md:grid-cols-4 gap-8">
                    <div>
                        <div className="flex items-center gap-3 mb-4">
                            <img src="/AudioScholar - No BG.png" alt="AudioScholar Logo" className="h-14 w-auto" />
                            <span className="font-montserrat font-semibold font-bold text-[22px] leading-7 tracking-normal text-primary-400">AudioScholar</span>
                        </div>
                        <p className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 mb-4">Transform your audio experience with AI-powered solutions.</p>
                        <div className="flex space-x-4">
                            <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                            </a>
                            <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                            </a>
                            <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                            </a>
                        </div>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Product</h3>
                        <ul className="space-y-2">
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Features</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Pricing</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Documentation</a></li>
                        </ul>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Company</h3>
                        <ul className="space-y-2">
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">About</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Blog</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Careers</a></li>
                        </ul>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Legal</h3>
                        <ul className="space-y-2">
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Privacy</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Terms</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Contact</a></li>
                        </ul>
                    </div>
                </div>

                <div className="border-t border-gray-800 mt-12 pt-8 text-center text-gray-400">
                    <p className="font-inter font-normal text-xs leading-4 tracking-normal">&copy; {new Date().getFullYear()} AudioScholar. All rights reserved.</p>
                </div>
            </div>
        </footer>
    );
};

const HomePage = () => {
    return (
        <>
            <Header />
            <main>
                <HeroSection />
                <Features />
                <Testimonials />
                <Pricing />
            </main>
            <Footer />
        </>
    );
};

export default HomePage; 