import React, { useEffect, useState } from 'react';
import { FiGrid, FiLogIn, FiLogOut, FiMic, FiUpload, FiUser, FiUserPlus, FiCheckCircle, FiYoutube, FiCloud, FiBriefcase } from 'react-icons/fi';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';

export const Header = () => {
    const navigate = useNavigate();
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    useEffect(() => {
        const token = localStorage.getItem('AuthToken');
        setIsAuthenticated(!!token);
    }, []);

    const handleLogout = async () => {
        const token = localStorage.getItem('AuthToken'); // Get token before clearing
        
        // 1. Perform local logout immediately
        console.log('Performing local logout...');
        localStorage.removeItem('AuthToken');
        localStorage.removeItem('userId');
        setIsAuthenticated(false);
        navigate('/');
        console.log('Local logout complete, user redirected.');

        // 2. Call backend logout endpoint (fire and forget, mostly)
        if (token) {
            console.log('Calling backend logout endpoint...');
            try {
                 // Ensure API_BASE_URL is defined or import it if necessary
                 // If you use axios elsewhere, you can use that instead of fetch
                 const response = await fetch(`${API_BASE_URL}api/auth/logout`, { // Assuming API_BASE_URL is available
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        // No 'Content-Type' needed for empty body POST
                    },
                    // No body needed for this endpoint based on controller
                 });

                 if (response.ok) {
                    console.log('Backend logout successful.');
                 } else {
                     // Log error but don't show to user as they are already logged out locally
                     const errorBody = await response.text(); // Get text response in case it's not JSON
                     console.error(`Backend logout failed: ${response.status} - ${errorBody}`);
                 }
            } catch (error) {
                 // Log network or other errors
                 console.error('Error calling backend logout endpoint:', error);
            }
        } else {
             console.warn('No token found, skipping backend logout call.');
        }
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
        <section className="py-20 bg-gradient-to-r from-blue-50 to-teal-50">
            <div className="container mx-auto px-4 text-center">
                <h1 className="font-montserrat font-bold text-4xl md:text-5xl leading-tight tracking-normal text-gray-800 mb-4">
                    Stop Taking Notes, Start <span className="text-[#1A365D]">Actively Listening</span>.
                </h1>
                <p className="font-montserrat font-semibold text-lg md:text-xl leading-relaxed tracking-normal text-gray-600 max-w-3xl mx-auto mb-8">
                    Traditional note-taking is inefficient. AudioScholar records lectures and uses AI to generate smart summaries,
                    so you can focus on understanding in class and get organized study materials automatically.
                </p>
                <Link to="/signup" className="bg-[#2D8A8A] hover:bg-teal-700 text-white font-inter font-medium text-base leading-5 tracking-tight py-3 px-8 rounded-md transition-colors shadow-md">
                    Get Started for Free
                </Link>
            </div>
        </section>
    );
};

const Features = () => {
    const features = [
        {
            icon: <FiMic className="w-6 h-6 text-teal-600" />,
            title: "Lecture Recording (Offline Capable)",
            description: "Easily record lectures on your mobile, even offline. Upload pre-recorded audio via mobile or web. Focus on listening, not writing."
        },
        {
            icon: <FiCheckCircle className="w-6 h-6 text-indigo-600" />,
            title: "AI-Powered Summaries & Notes",
            description: "Our AI processes your audio after the lecture to automatically generate structured summaries, key points, and topic lists."
        },
        {
            icon: <FiYoutube className="w-6 h-6 text-red-600" />,
            title: "Personalized Recommendations",
            description: "Get relevant YouTube video recommendations based on your lecture content to deepen your understanding."
        },
        {
            icon: <FiUpload className="w-6 h-6 text-purple-600" />,
            title: "PowerPoint Context (Optional)",
            description: "Optionally upload lecture slides to provide context, enhancing the accuracy and relevance of AI summaries."
        },
        {
            icon: <FiCloud className="w-6 h-6 text-blue-600" />,
            title: "Optional Cloud Sync",
            description: "Securely sync recordings and notes to the cloud for backup and access across devices (manual or automatic)."
        },
        {
            icon: <FiBriefcase className="w-6 h-6 text-gray-600" />,
            title: "Web Access & Management",
            description: "Access your recordings, summaries, and recommendations, or upload audio files easily through our web interface."
        }
    ];

    return (
        <section className="py-16 bg-teal-50">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-3xl leading-[36px] tracking-normal text-center text-[#1A365D] mb-12">How AudioScholar Makes Learning Efficient</h2>
                <div className="grid md:grid-cols-3 gap-8">
                    {features.map((feature, index) => (
                        <div key={index} className="bg-white p-6 rounded-lg border border-gray-200 hover:shadow-lg transition-shadow">
                            <div className="flex items-center mb-4">
                                <div className="w-12 h-12 bg-teal-100 rounded-full flex items-center justify-center mr-4">
                                    {feature.icon}
                                </div>
                                <h3 className="font-inter font-semibold text-lg leading-6 tracking-tight text-teal-700">{feature.title}</h3>
                            </div>
                            <p className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-600">
                                {feature.description}
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
            quote: "AudioScholar genuinely made my note-taking process efficient. I can actually pay attention in lectures now and trust I'll have great notes later!",
            author: "Alex R.",
            role: "University Student"
        },
        {
            quote: "The AI summaries are a lifesaver. Reviewing lectures used to take hours, but now it's much faster and I feel like I understand the material better.",
            author: "Samantha K.",
            role: "College Student"
        },
        {
            quote: "Being able to record offline is crucial on campus. AudioScholar handles it perfectly, reducing my stress about missing important points.",
            author: "Mike T.",
            role: "Postgraduate Student"
        }
    ];

    return (
        <section className="py-16 bg-white">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-gray-800 mb-12">Focus More, Study Smarter</h2>
                <div className="grid md:grid-cols-3 gap-8">
                    {testimonials.map((testimonial, index) => (
                        <div key={index} className="bg-gray-50 p-6 rounded-lg border border-gray-200 shadow-sm transform hover:scale-105 transition-transform duration-300">
                            <div className="text-yellow-500 mb-4">
                                {[...Array(5)].map((_, i) => (
                                    <span key={i}>★</span>
                                ))}
                            </div>
                            <p className="font-inter font-normal text-base leading-6 tracking-wide italic text-gray-700 mb-4">"{testimonial.quote}"</p>
                            <div>
                                <p className="text-gray-800 font-inter font-semibold text-sm leading-5 tracking-tight">
                                    {testimonial.author}
                                </p>
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
            description: "Essential tools for efficient lecture capture.",
            features: [
                "Mobile Lecture Recording (Foreground)",
                "Audio Upload (Mobile & Web)",
                "Standard AI Summaries & Notes",
                "Basic YouTube Recommendations",
                "Local Storage"
            ],
            cta: "Get Started Free"
        },
        {
            name: "Premium",
            price: "$9.99",
            description: "Unlock advanced features & cloud power.",
            features: [
                "Includes all Free features, plus:",
                "Background Mobile Recording",
                "Enhanced AI Summaries (e.g., w/ PPT Context)",
                "Expanded Learning Recommendations",
                "Optional Cloud Sync & Backup",
                "Priority Support (Future)"
            ],
            cta: "Go Premium",
            featured: true
        }
    ];

    return (
        <section className="py-16 bg-[#1A365D]">
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-[28px] leading-[36px] tracking-normal text-center text-white mb-4">Choose Your Plan</h2>
                <p className="font-inter font-normal text-lg leading-5 tracking-normal text-center text-teal-200 mb-12">Start learning efficiently today.</p>

                <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
                    {plans.map((plan, index) => (
                        <div
                            key={index}
                            className={`p-8 rounded-lg shadow-lg border ${plan.featured ? 'border-teal-400 bg-white scale-105' : 'border-gray-200 bg-gray-50'} transition-transform duration-300`}
                        >
                            {plan.featured && (
                                <div className="bg-teal-500 text-white font-inter font-medium text-[11px] leading-4 tracking-wide py-1 px-3 rounded-full inline-block mb-4 absolute -top-3 right-4">
                                    Most Popular
                                </div>
                            )}
                            <h3 className={`font-montserrat font-semibold text-[24px] leading-[32px] tracking-normal mb-1 ${plan.featured ? 'text-[#1A365D]' : 'text-gray-800'}`}>{plan.name}</h3>
                            <p className={`font-inter font-normal text-sm mb-2 ${plan.featured ? 'text-gray-600' : 'text-gray-500'}`}>{plan.description}</p>
                            <p className={`text-3xl font-bold mb-6 ${plan.featured ? 'text-teal-600' : 'text-gray-900'}`}>{plan.price}<span className="text-sm font-normal text-gray-500">{plan.name === 'Premium' ? '/month' : ''}</span></p>
                            <ul className="my-6 space-y-3">
                                {plan.features.map((feature, i) => (
                                    <li key={i} className={`flex items-start font-inter font-normal text-sm leading-5 tracking-normal ${plan.featured ? 'text-gray-700' : 'text-gray-600'}`}>
                                        <FiCheckCircle className={`h-5 w-5 mr-2 ${plan.featured ? 'text-teal-500' : 'text-gray-400'} flex-shrink-0 mt-0.5`} />
                                        {feature}
                                    </li>
                                ))}
                            </ul>
                            <Link to={plan.name === 'Free' ? '/signup' : '/signup?plan=premium'} className={`block w-full text-center py-3 px-4 rounded-md font-inter font-medium text-sm leading-5 tracking-tight transition-colors ${plan.featured ? 'bg-teal-500 hover:bg-teal-600 text-white shadow-md' : 'bg-white hover:bg-gray-100 text-teal-600 border border-teal-500'}`}>
                                {plan.cta}
                            </Link>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
};

// Define the new TeamSection component
const TeamSection = () => {
    const teamMembers = [
        { name: "Math Lee L. Biacolo", role: "Developer", imgSrc: "/448017950_422357707435559_1572405260216380511_n.jpg" },
        { name: "Nathan John G. Orlanes", role: "Developer", imgSrc: "/120552317_4626392360735881_682202529014384747_n (1).jpg" },
        { name: "Terence John N. Duterte", role: "Developer", imgSrc: "/image.jpg" },
    ];

    return (
        <section className="py-16 bg-white"> {/* Or choose another background e.g., bg-gray-100 */}
            <div className="container mx-auto px-4">
                <h2 className="font-montserrat font-semibold text-3xl leading-[36px] tracking-normal text-center text-gray-800 mb-12">Meet the Team</h2>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-4xl mx-auto">
                    {teamMembers.map((member, index) => (
                        <Link 
                            to="/about" 
                            key={index} 
                            className="block bg-gray-50 p-6 rounded-lg border border-gray-200 shadow-sm text-center hover:shadow-lg hover:scale-105 transition-all duration-300 ease-in-out"
                        >
                            {/* Use img tag with provided source */}
                             <img 
                                src={member.imgSrc} 
                                alt={member.name} 
                                className="w-24 h-24 rounded-full mx-auto mb-4 object-cover border-2 border-gray-200" // Added object-cover and border
                             />
                            <h3 className="font-inter font-semibold text-lg leading-6 tracking-tight text-gray-800 mb-1">{member.name}</h3>
                            <p className="font-inter font-normal text-sm leading-5 tracking-normal text-teal-600">{member.role}</p>
                        </Link>
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
                        <p className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 mb-4">Transform your lecture experience with AI-powered notes.</p>
                        <div className="flex space-x-4">
                            {/* Add social links if available */}
                        </div>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Product</h3>
                        <ul className="space-y-2">
                            <li><Link to="/#features" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Features</Link></li>
                            <li><Link to="/#pricing" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Pricing</Link></li>
                             {/* Add link to Docs if applicable */}
                        </ul>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Company</h3>
                        <ul className="space-y-2">
                             {/* Add Blog/Careers links if applicable */}
                        </ul>
                    </div>

                    <div>
                        <h3 className="font-inter font-semibold text-base leading-6 tracking-tight text-white mb-4">Legal</h3>
                        <ul className="space-y-2">
                             {/* Add Privacy/Terms/Contact links */}
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Privacy Policy</a></li>
                            <li><a href="#" className="font-inter font-normal text-sm leading-5 tracking-normal text-gray-400 hover:text-primary-400 transition-colors">Terms of Service</a></li>
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
                <TeamSection />
            </main>
            <Footer />
        </>
    );
};

export default HomePage; 