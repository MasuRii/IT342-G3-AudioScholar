import React from 'react';
import { Link } from 'react-router-dom';
import { Header, Footer } from './../Home/HomePage'; // Import Header/Footer from HomePage

const AboutPage = () => {
  return (
    <div className="flex flex-col min-h-screen">
      <Header />
      <main className="flex-grow">
        {/* Hero Section */}
        <section className="bg-gradient-to-r from-[#1A365D] to-[#2D8A8A] py-20 text-white text-center">
          <div className="container mx-auto px-4">
            <h1 className="text-4xl md:text-5xl font-bold mb-4">About AudioScholar</h1>
            <p className="text-xl md:text-2xl text-indigo-100">
              Revolutionizing Learning Through Intelligent Audio Processing
            </p>
          </div>
        </section>

        {/* Our Mission */}
        <section className="py-16 bg-white">
          <div className="container mx-auto px-4 max-w-4xl">
            <h2 className="text-3xl font-bold text-center text-gray-800 mb-12">Our Mission</h2>
            <p className="text-lg text-gray-700 leading-relaxed text-center mb-6">
              At AudioScholar, our mission is to empower students, professionals, and lifelong learners
              by transforming spoken knowledge into accessible, structured, and actionable insights.
              We believe that valuable information shared in lectures, meetings, and discussions
              should be easily captured, understood, and revisited.
            </p>
            <p className="text-lg text-gray-700 leading-relaxed text-center">
              We leverage the power of cutting-edge AI to automate the tedious process of note-taking
              and summarizing, allowing you to focus on what truly matters: learning and comprehension.
            </p>
          </div>
        </section>

        {/* How It Works / Features Summary (Optional but recommended) */}
        <section className="py-16 bg-teal-50"> 
          <div className="container mx-auto px-4 max-w-4xl">
            <h2 className="text-3xl font-bold text-center text-[#1A365D] mb-12">How It Works</h2>
             <p className="text-lg text-gray-700 leading-relaxed text-center mb-6">
                AudioScholar simplifies capturing and understanding audio. Upload your recordings, and our AI
                generates summaries, identifies key points, and provides helpful insights.
                Access everything easily through your dashboard.
             </p>
             {/* Link back to main features section or signup */}
             <div className="text-center mt-8">
                <Link 
                    to="/#features" // Link to features section on homepage
                    className="text-teal-600 hover:text-teal-800 font-semibold mr-4"
                >
                    Learn More About Features
                </Link>
                <Link 
                    to="/signup"
                    className="bg-teal-500 hover:bg-teal-600 text-white font-bold py-2 px-6 rounded-lg transition-colors"
                >
                    Get Started
                </Link>
             </div>
          </div>
        </section>

        {/* Our Vision */}
        <section className="py-16 bg-white">
          <div className="container mx-auto px-4 max-w-4xl text-center">
            <h2 className="text-3xl font-bold text-gray-800 mb-6">Our Vision</h2>
            <p className="text-lg text-gray-700 leading-relaxed">
              We envision a future where learning from audio is as efficient and effective as reading.
              AudioScholar aims to be the essential tool for anyone looking to maximize their comprehension
              and retention of spoken information.
            </p>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
};

export default AboutPage; 